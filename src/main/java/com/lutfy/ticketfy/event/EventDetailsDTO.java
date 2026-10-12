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
        Instant createdAt,
        boolean featured,
        Instant cancelledAt,
        String cancellationReason,
        boolean salesClosed
) {
    public EventDetailsDTO(Event event, Instant now) {
        this(event.getId(), event.getName(), event.getDescription(), event.getImageUrl(), event.getVenueName(),
                event.getAddress(), event.getCity(), event.getState(), event.getStartsAt(),
                event.getEndsAt(), event.getOrganizer().getName(), event.getOrganizer().getId(), event.getCreatedAt(),
                event.isFeatured(), event.getCancelledAt(), event.getCancellationReason(),
                event.isCancelled() || !event.getStartsAt().isAfter(now));
    }
}