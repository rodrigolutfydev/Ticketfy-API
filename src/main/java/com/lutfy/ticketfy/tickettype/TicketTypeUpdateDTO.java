package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.infra.validation.NullOrNotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TicketTypeUpdateDTO(
        @Size(max = 150) @NullOrNotBlank
        String name,
        @Size(max = 500)
        String description,
        @PositiveOrZero
        BigDecimal price,
        @Positive
        Integer quantityTotal,
        @Positive
        Integer maxPerOrder,
        @PositiveOrZero
        Integer maxPerBuyer
) {
    public TicketTypeUpdateDTO {
        if (description != null && description.isBlank()) description = "";
    }
}
