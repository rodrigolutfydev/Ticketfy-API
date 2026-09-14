package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeDetails(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer quantityTotal,
        Integer available,
        Integer maxPerOrder,
        Boolean active
) {
    public TicketTypeDetails(TicketType ticketType) {
        this(ticketType.getId(), ticketType.getName(), ticketType.getDescription(), ticketType.getPrice(),
        ticketType.getQuantityTotal(), ticketType.availableQuantity(), ticketType.getMaxPerOrder(), ticketType.getActive());
    }
}
