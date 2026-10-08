package com.lutfy.ticketfy.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketDetailsDTO(
        UUID id,
        String code,
        TicketStatus status,
        String eventName,
        Instant eventStartsAt,
        String ticketTypeName,
        Instant usedAt,
        Instant createdAt,
        boolean transferable
) {
    public TicketDetailsDTO(Ticket ticket, boolean transferable) {
        this(ticket.getId(),
                ticket.getCode(),
                ticket.getStatus(),
                ticket.getTicketType().getEvent().getName(),
                ticket.getTicketType().getEvent().getStartsAt(),
                ticket.getTicketType().getName(),
                ticket.getUsedAt(),
                ticket.getCreatedAt(),
                transferable);
    }
}