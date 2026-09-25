package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderDetailsDTO(
       UUID id,
       OrderStatus status,
       BigDecimal totalAmount,
       LocalDateTime expiresAt,
       LocalDateTime createdAt,
       List<OrderItemDTO> items
) {
    public OrderDetailsDTO(Order order) {
        this(   order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemDTO::new).toList()
        );
    }
}
