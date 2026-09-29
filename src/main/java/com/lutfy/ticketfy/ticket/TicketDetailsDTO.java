package com.lutfy.ticketfy.ticket;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketDetailsDTO(
        UUID id,
        String code,
        TicketStatus status,
        String eventName,
        LocalDateTime eventStartsAt,
        String ticketTypeName,
        LocalDateTime usedAt,
        LocalDateTime createdAt
) {
    public TicketDetailsDTO(Ticket ticket) {
        this(ticket.getId(),
                ticket.getCode(),
                ticket.getStatus(),
                ticket.getTicketType().getEvent().getName(),
                ticket.getTicketType().getEvent().getStartsAt(),
                ticket.getTicketType().getName(),
                ticket.getUsedAt(),
                ticket.getCreatedAt());
    }
}