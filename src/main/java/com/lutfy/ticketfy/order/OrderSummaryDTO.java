package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryDTO(
        UUID id,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public OrderSummaryDTO(Order order) {
        this(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}
