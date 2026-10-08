package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.ticket.Ticket;

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
       List<OrderItemDTO> items,
       List<OrderTicketDTO> tickets,
       boolean eventCancelled
) {
    public OrderDetailsDTO(Order order, List<Ticket> tickets) {
        this( order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemDTO::new).toList(),
                tickets.stream().map(ticket -> new OrderTicketDTO(ticket, order.getUser().getId())).toList(),
                order.belongsToCancelledEvent()
        );
    }
}
