package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeDetailsDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer quantityTotal,
        Integer quantitySold,
        Integer available,
        Integer maxPerOrder,
        Boolean active
) {
    public TicketTypeDetailsDTO(TicketType ticketType) {
        this(ticketType.getId(), ticketType.getName(), ticketType.getDescription(), ticketType.getPrice(),
        ticketType.getQuantityTotal(), ticketType.getQuantitySold(), ticketType.availableQuantity(), ticketType.getMaxPerOrder(), ticketType.getActive());
    }
}
