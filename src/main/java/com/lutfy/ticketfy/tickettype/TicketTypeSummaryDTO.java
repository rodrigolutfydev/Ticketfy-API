package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeSummaryDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer maxPerOrder,
        boolean soldOut,
        Integer remaining
) {
    static final int LOW_STOCK_THRESHOLD = 10;

    public TicketTypeSummaryDTO(TicketType ticketType) {
        this(ticketType.getId(), ticketType.getName(), ticketType.getDescription(), ticketType.getPrice(),
                ticketType.getMaxPerOrder(), ticketType.availableQuantity() <= 0,
                lowStockRemaining(ticketType.availableQuantity()));
    }

    private static Integer lowStockRemaining(int available) {
        return available > 0 && available <= LOW_STOCK_THRESHOLD ? available : null;
    }
}
