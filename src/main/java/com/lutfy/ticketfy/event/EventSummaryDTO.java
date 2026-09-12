package com.lutfy.ticketfy.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventSummaryDTO(
        UUID id,
        String name,
        String venueName,
        String city,
        LocalDateTime startsAt
) {
    public EventSummaryDTO(Event event) {
        this(event.getId(), event.getName(), event.getVenueName(), event.getCity(), event.getStartsAt());
    }
}