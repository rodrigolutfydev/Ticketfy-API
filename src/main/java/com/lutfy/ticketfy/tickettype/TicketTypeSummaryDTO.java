package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeSummaryDTO(
        UUID id,
        String name,
        BigDecimal price,
        Integer available
) {
    public TicketTypeSummaryDTO(TicketType ticketType) {
        this(ticketType.getId(), ticketType.getName(), ticketType.getPrice(), ticketType.availableQuantity());
    }
}
