package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeSummary(
        UUID id,
        String name,
        BigDecimal price,
        Integer available
) {
    public TicketTypeSummary(TicketType ticketType) {
        this(ticketType.getId(), ticketType.getName(), ticketType.getPrice(), ticketType.availableQuantity());
    }
}
