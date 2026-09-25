package com.lutfy.ticketfy.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDTO(
        UUID ticketTypeId,
        String ticketTypeName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
    public OrderItemDTO(OrderItem item) {
        this(   item.getTicketType().getId(),
                item.getTicketType().getName(),
                item.getUnitPrice(), item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
