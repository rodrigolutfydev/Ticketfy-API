package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.order.CancelledEventOrderProcessor;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventCancellationService {

    private static final Logger log = LoggerFactory.getLogger(EventCancellationService.class);
    private static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PAID);

    private final EventService eventService;
    private final OrderRepository orderRepository;
    private final CancelledEventOrderProcessor processor;

    public EventCancellationService(EventService eventService, OrderRepository orderRepository,
                                    CancelledEventOrderProcessor processor) {
        this.eventService = eventService;
        this.orderRepository = orderRepository;
        this.processor = processor;
    }

    public EventCancellationResultDTO cancel(UUID eventId, EventCancellationDTO dto, User requester) {
        var cancelledAt = eventService.markCancelled(eventId, normalizeReason(dto), requester);
        var tally = processAll(orderRepository.findIdsByEventIdAndStatusIn(eventId, OPEN_STATUSES));
        return new EventCancellationResultDTO(eventId, cancelledAt,
                tally.refunded, tally.cancelled, tally.failed, tally.requiresManualAction);
    }

    public int processPendingOrders() {
        var tally = processAll(orderRepository.findIdsAwaitingEventCancellation(OPEN_STATUSES));
        return tally.refunded + tally.cancelled;
    }

    private Tally processAll(List<UUID> orderIds) {
        var tally = new Tally();
        for (var orderId : orderIds) {
            try {
                switch (processor.process(orderId)) {
                    case REFUNDED -> tally.refunded++;
                    case CANCELLED -> tally.cancelled++;
                    case REQUIRES_MANUAL_ACTION -> tally.requiresManualAction++;
                    case SKIPPED -> { }
                }
            } catch (RuntimeException ex) {
                tally.failed++;
                log.error("Order {} of a cancelled event could not be processed and will be retried", orderId, ex);
            }
        }
        return tally;
    }

    private static String normalizeReason(EventCancellationDTO dto) {
        if (dto == null || dto.reason() == null || dto.reason().isBlank()) return null;
        return dto.reason().trim();
    }

    private static final class Tally {
        private int refunded;
        private int cancelled;
        private int failed;
        private int requiresManualAction;
    }
}
