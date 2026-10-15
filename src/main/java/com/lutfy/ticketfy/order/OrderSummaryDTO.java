package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryDTO(
        UUID id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        UUID eventId,
        String eventName,
        Instant eventStartsAt
) {
    public OrderSummaryDTO(Order order, OrderEventRef event) {
        this(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt(),
                event.eventId(), event.eventName(), event.eventStartsAt());
    }
}
