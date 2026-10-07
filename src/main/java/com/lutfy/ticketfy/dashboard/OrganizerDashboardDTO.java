package com.lutfy.ticketfy.dashboard;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrganizerDashboardDTO(
        Totals totals,
        Page<EventSales> events
) {
    public record Totals(long events, long ticketsSold, BigDecimal revenue) {}

    public record EventSales(
            UUID eventId,
            String name,
            Instant startsAt,
            String imageUrl,
            long ticketsSold,
            long capacity,
            BigDecimal percentSold,
            BigDecimal revenue
    ) {}
}
