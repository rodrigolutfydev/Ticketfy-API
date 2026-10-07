package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.ticket.TicketRepository;
import com.lutfy.ticketfy.ticket.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CancelledEventOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(CancelledEventOrderProcessor.class);

    public enum Outcome { REFUNDED, CANCELLED, REQUIRES_MANUAL_ACTION, SKIPPED }

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final TicketRepository ticketRepository;

    public CancelledEventOrderProcessor(OrderRepository orderRepository, OrderService orderService,
                                        TicketRepository ticketRepository) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome process(UUID orderId) {
        var order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.belongsToCancelledEvent()) {
            return Outcome.SKIPPED;
        }
        return switch (order.getStatus()) {
            case PENDING -> {
                orderService.cancelPending(order);
                yield Outcome.CANCELLED;
            }
            case PAID -> {
                if (ticketRepository.existsByOrderIdAndStatus(orderId, TicketStatus.USED)) {
                    log.warn("Order {} of a cancelled event has used tickets and requires manual refund", orderId);
                    yield Outcome.REQUIRES_MANUAL_ACTION;
                }
                orderService.refundPaid(order);
                yield Outcome.REFUNDED;
            }
            default -> Outcome.SKIPPED;
        };
    }
}
