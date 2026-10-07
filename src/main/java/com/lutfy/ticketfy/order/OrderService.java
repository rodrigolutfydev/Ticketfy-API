package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.*;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.payout.LedgerService;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.ticket.TicketService;
import com.lutfy.ticketfy.tickettype.TicketTypeRepository;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final EventRepository eventRepository;
    private final LedgerService ledgerService;
    private final PayoutSettings payoutSettings;
    private final long reservationMinutes;
    private final long refundDeadlineHours;

    public OrderService(OrderRepository orderRepository,
                        TicketTypeRepository ticketTypeRepository,
                        TicketService ticketService,
                        PaymentService paymentService,
                        EventRepository eventRepository,
                        LedgerService ledgerService,
                        PayoutSettings payoutSettings,
                        @Value("${ticketfy.order.reservation-minutes}") long reservationMinutes,
                        @Value("${ticketfy.refund.deadline-hours}") long refundDeadlineHours) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketService = ticketService;
        this.paymentService = paymentService;
        this.eventRepository = eventRepository;
        this.ledgerService = ledgerService;
        this.payoutSettings = payoutSettings;
        this.reservationMinutes = reservationMinutes;
        this.refundDeadlineHours = refundDeadlineHours;
    }

    @Transactional
    public OrderDetailsDTO create(OrderCreationDTO dto, String idempotencyKey, User authenticated) {
        if (idempotencyKey != null) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                checkOwnership(existing.get(), authenticated);
                return new OrderDetailsDTO(existing.get());
            }
        }
        var expiresAt = Instant.now().plus(Duration.ofMinutes(reservationMinutes));
        var order = new Order(authenticated, expiresAt, idempotencyKey);
        UUID eventId = null;
        for (var itemRequest : dto.items()) {
            var ticketType = ticketTypeRepository.findByIdAndActiveTrueAndEventActiveTrueAndEventCancelledAtIsNull(itemRequest.ticketTypeId())
                    .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found"));
            var itemEventId = ticketType.getEvent().getId();
            if (eventId == null) {
                eventId = itemEventId;
            } else if (!eventId.equals(itemEventId)) {
                throw new MixedEventsOrderException("All items of an order must belong to the same event");
            }
            if (ticketType.getMaxPerOrder() != null
                    && itemRequest.quantity() > ticketType.getMaxPerOrder()) {
                throw new MaxPerOrderExceededException(
                        "Maximum " + ticketType.getMaxPerOrder() + " tickets per order for this ticket type");
            }
            int affectedRows = ticketTypeRepository.reserveStock(ticketType.getId(), itemRequest.quantity());
            if (affectedRows == 0) {
                if (eventRepository.existsByIdAndCancelledAtIsNotNull(ticketType.getEvent().getId())) {
                    throw new InvalidEventStateException("Event was cancelled");
                }
                throw new InsufficientStockException("Not enough tickets available for this ticket type");
            }
            var item = new OrderItem(order, ticketType, itemRequest.quantity());
            order.addItem(item);
        }
        order.applyPlatformFee(payoutSettings.platformFeePercent());
        var saved = orderRepository.saveAndFlush(order);
        return new OrderDetailsDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrderDetailsDTO findById(UUID id, User authenticated) {
        var order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        checkOwnership(order, authenticated);
        return new OrderDetailsDTO(order);
    }

    @Transactional(readOnly = true)
    public Optional<OrderDetailsDTO> findByIdempotencyKey(String idempotencyKey, User authenticated) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(order -> {
                    checkOwnership(order, authenticated);
                    return new OrderDetailsDTO(order);
                });
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> listMyOrders(User authenticated, Pageable pageable) {
        return orderRepository.findByUserId(authenticated.getId(), pageable)
                .map(OrderSummaryDTO::new);
    }

    @Transactional
    public OrderDetailsDTO cancel(UUID id, User authenticated) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        checkOwnership(order, authenticated);
        cancelPending(order);
        return new OrderDetailsDTO(order);
    }

    @Transactional
    public void cancelPending(Order order) {
        order.cancel();
        releaseStock(order);
    }

    @Transactional
    public OrderDetailsDTO refund(UUID id, User authenticated) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        checkOwnership(order, authenticated);
        var deadline = Instant.now().plus(Duration.ofHours(refundDeadlineHours));
        for (var item : order.getItems()) {
            if (item.getTicketType().getEvent().getStartsAt().isBefore(deadline)) {
                throw new InvalidOrderStateException("The refund period for this event has ended");
            }
        }

        refundPaid(order);
        return new OrderDetailsDTO(order);
    }

    @Transactional
    public void refundPaid(Order order) {
        order.refund();
        ticketService.cancelForRefund(order.getId());
        releaseStock(order);
        paymentService.refundApproved(order);
        ledgerService.recordRefund(order);
    }

    private void releaseStock(Order order) {
        for (var item : order.getItems()) {
            ticketTypeRepository.releaseStock(item.getTicketType().getId(), item.getQuantity());
        }
    }

    private void checkOwnership(Order order, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!order.getUser().equals(authenticated)) {
            throw new OrderAccessDeniedException("You do not own this order");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findOverdueOrderIds() {
        return orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING, Instant.now())
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Transactional
    public boolean expireOrder(UUID id) {
        var order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        order.expire();
        releaseStock(order);
        return true;
    }
}
