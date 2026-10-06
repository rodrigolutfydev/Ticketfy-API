package com.lutfy.ticketfy.event;

import java.time.Instant;
import java.util.UUID;

public record EventDetailsDTO(
        UUID id,
        String name,
        String description,
        String imageUrl,
        String venueName,
        String address,
        String city,
        String state,
        Instant startsAt,
        Instant endsAt,
        String organizerName,
        UUID organizerId,
        Instant createdAt
) {
    public EventDetailsDTO(Event event) {
        this(event.getId(), event.getName(), event.getDescription(), event.getImageUrl(), event.getVenueName(),
                event.getAddress(), event.getCity(), event.getState(), event.getStartsAt(),
                event.getEndsAt(), event.getOrganizer().getName(), event.getOrganizer().getId(), event.getCreatedAt());
    }
}