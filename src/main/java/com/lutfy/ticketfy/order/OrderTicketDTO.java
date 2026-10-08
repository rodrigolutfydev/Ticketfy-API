package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.ticket.Ticket;
import com.lutfy.ticketfy.ticket.TicketStatus;

import java.util.UUID;

public record OrderTicketDTO(
        UUID id,
        String ticketTypeName,
        TicketStatus status,
        String code,
        boolean transferred
) {
    public OrderTicketDTO(Ticket ticket, UUID buyerId) {
        this(ticket.getId(),
                ticket.getTicketType().getName(),
                ticket.getStatus(),
                ticket.getOwner().getId().equals(buyerId) ? ticket.getCode() : null,
                !ticket.getOwner().getId().equals(buyerId));
    }
}
