package com.lutfy.ticketfy.event;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryDTO(
        UUID id,
        String name,
        String venueName,
        String city,
        Instant startsAt
) {
    public EventSummaryDTO(Event event) {
        this(event.getId(), event.getName(), event.getVenueName(), event.getCity(), event.getStartsAt());
    }
}