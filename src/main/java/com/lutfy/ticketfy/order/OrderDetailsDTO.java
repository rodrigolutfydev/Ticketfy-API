package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailsDTO(
       UUID id,
       OrderStatus status,
       BigDecimal totalAmount,
       Instant expiresAt,
       Instant createdAt,
       List<OrderItemDTO> items
) {
    public OrderDetailsDTO(Order order) {
        this( order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemDTO::new).toList()
        );
    }
}
