package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lutfy.ticketfy.dashboard.DashboardMath.average;
import static com.lutfy.ticketfy.dashboard.DashboardMath.money;
import static com.lutfy.ticketfy.dashboard.DashboardMath.percent;

@Service
public class DashboardService {

    private static final List<OrderStatus> STATUS_ORDER = List.of(
            OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.REFUNDED);

    private final EventRepository eventRepository;
    private final DashboardQueryRepository queries;
    private final ZoneId zone;

    public DashboardService(EventRepository eventRepository,
                            DashboardQueryRepository queries,
                            @Value("${ticketfy.dashboard.time-zone}") String timeZone) {
        this.eventRepository = eventRepository;
        this.queries = queries;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public EventDashboardDTO eventDashboard(UUID eventId, User requester) {
        var event = findOwnedEvent(eventId, requester);
        var lots = queries.findLotSales(eventId);
        var statusCounts = queries.countByStatus(eventId).stream()
                .collect(Collectors.toMap(DashboardQueryRepository.StatusCount::status, Function.identity()));
        var checkIns = queries.countCheckIns(eventId);

        long sold = 0, reserved = 0, capacity = 0, remaining = 0;
        var revenue = BigDecimal.ZERO;
        var ticketTypes = new ArrayList<EventDashboardDTO.TicketTypeSales>();
        for (var lot : lots) {
            sold += lot.sold();
            reserved += lot.reserved();
            capacity += lot.quantityTotal();
            remaining += lot.quantityTotal() - lot.quantitySold();
            revenue = revenue.add(money(lot.revenue()));
            ticketTypes.add(new EventDashboardDTO.TicketTypeSales(
                    lot.id(), lot.name(), money(lot.price()), lot.quantityTotal(), lot.sold(), lot.reserved(),
                    lot.quantityTotal() - lot.quantitySold(), money(lot.revenue()),
                    percent(lot.sold(), lot.quantityTotal())));
        }

        var paid = statusCounts.get(OrderStatus.PAID);
        long paidOrders = paid == null ? 0 : paid.orders();
        var totals = new EventDashboardDTO.Totals(
                sold, reserved, capacity, remaining, percent(sold, capacity), money(revenue), paidOrders,
                average(revenue, paidOrders), paidOrders == 0 ? null : average(revenue, sold));

        var ordersByStatus = STATUS_ORDER.stream()
                .map(status -> {
                    var count = statusCounts.get(status);
                    return count == null
                            ? new EventDashboardDTO.StatusSummary(status, 0, 0)
                            : new EventDashboardDTO.StatusSummary(status, count.orders(), count.tickets());
                })
                .toList();

        var checkIn = new EventDashboardDTO.CheckIn(
                checkIns.checkedIn(), checkIns.issued(), percent(checkIns.checkedIn(), checkIns.issued()));

        return new EventDashboardDTO(event.getId(), event.getName(), event.getStartsAt(), zone.getId(), Instant.now(),
                totals, ordersByStatus, checkIn, ticketTypes, continuousDailySales(eventId));
    }

    private List<EventDashboardDTO.DailySales> continuousDailySales(UUID eventId) {
        var rows = queries.findDailySales(eventId, zone);
        if (rows.isEmpty()) return List.of();
        var byDay = rows.stream()
                .collect(Collectors.toMap(DashboardQueryRepository.DailySales::date, Function.identity()));
        var series = new ArrayList<EventDashboardDTO.DailySales>();
        var last = rows.get(rows.size() - 1).date();
        for (var day = rows.get(0).date(); !day.isAfter(last); day = day.plusDays(1)) {
            var row = byDay.get(day);
            series.add(row == null
                    ? new EventDashboardDTO.DailySales(day, 0, money(BigDecimal.ZERO))
                    : new EventDashboardDTO.DailySales(day, row.tickets(), money(row.revenue())));
        }
        return series;
    }

    @Transactional(readOnly = true)
    public Page<EventOrderDTO> eventOrders(UUID eventId, OrderStatus status, String search,
                                           Pageable pageable, User requester) {
        findOwnedEvent(eventId, requester);
        var page = queries.findEventOrders(eventId, status, normalize(search),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        var orderIds = page.getContent().stream().map(DashboardQueryRepository.EventOrder::id).toList();
        var itemsByOrder = queries.findEventOrderItems(eventId, orderIds).stream()
                .collect(Collectors.groupingBy(DashboardQueryRepository.EventOrderItem::orderId));

        return page.map(order -> new EventOrderDTO(
                order.id(), order.status(), order.createdAt(), order.paidAt(),
                new EventOrderDTO.Buyer(order.buyerName(), order.buyerEmail()),
                itemsByOrder.getOrDefault(order.id(), List.of()).stream()
                        .map(item -> new EventOrderDTO.Item(item.ticketTypeId(), item.ticketTypeName(), item.quantity(),
                                money(item.unitPrice()),
                                money(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))))
                        .toList(),
                money(order.total())));
    }

    @Transactional(readOnly = true)
    public OrganizerDashboardDTO organizerDashboard(Pageable pageable, User requester) {
        var events = eventRepository.findByOrganizerIdAndActiveTrue(requester.getId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startsAt", "id")));
        var eventIds = events.getContent().stream().map(Event::getId).toList();
        var salesByEvent = queries.findSalesByEvent(eventIds).stream()
                .collect(Collectors.toMap(DashboardQueryRepository.EventSales::eventId, Function.identity()));

        var page = events.map(event -> {
            var sales = salesByEvent.get(event.getId());
            long sold = sales == null ? 0 : sales.sold();
            long capacity = sales == null ? 0 : sales.capacity();
            return new OrganizerDashboardDTO.EventSales(event.getId(), event.getName(), event.getStartsAt(),
                    event.getImageUrl(), sold, capacity, percent(sold, capacity),
                    money(sales == null ? null : sales.revenue()));
        });

        var overall = queries.findOrganizerSales(requester.getId());
        var totals = new OrganizerDashboardDTO.Totals(events.getTotalElements(), overall.sold(), money(overall.revenue()));
        return new OrganizerDashboardDTO(totals, page);
    }

    private Event findOwnedEvent(UUID eventId, User requester) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (requester.getRole() != Role.ADMIN && !event.getOrganizer().getId().equals(requester.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
        return event;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
