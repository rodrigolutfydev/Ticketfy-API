package com.lutfy.ticketfy.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDetailsDTO(
        UUID id,
        String name,
        String description,
        String venueName,
        String address,
        String city,
        String state,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String organizerName,
        LocalDateTime createdAt
) {
    public EventDetailsDTO(Event event) {
        this(event.getId(), event.getName(), event.getDescription(), event.getVenueName(),
                event.getAddress(), event.getCity(), event.getState(), event.getStartsAt(),
                event.getEndsAt(), event.getOrganizer().getName(), event.getCreatedAt());
    }
}