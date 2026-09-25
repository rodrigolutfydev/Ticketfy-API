package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderSummaryDTO(
        UUID id,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
    public OrderSummaryDTO(Order order) {
        this(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }
}
