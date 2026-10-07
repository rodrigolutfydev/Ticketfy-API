package com.lutfy.ticketfy.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSummaryDTO(
        UUID id,
        String name,
        String imageUrl,
        String venueName,
        String city,
        Instant startsAt,
        boolean featured,
        BigDecimal minPrice,
        boolean soldOut
) {
    public EventSummaryDTO(Event event, BigDecimal minPrice, boolean soldOut) {
        this(event.getId(), event.getName(), event.getImageUrl(), event.getVenueName(), event.getCity(), event.getStartsAt(),
                event.isFeatured(), minPrice, soldOut);
    }
}
