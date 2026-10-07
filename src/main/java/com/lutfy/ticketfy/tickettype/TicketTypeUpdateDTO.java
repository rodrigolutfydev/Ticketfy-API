package com.lutfy.ticketfy.tickettype;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Partial update: null keeps the current value
public record TicketTypeUpdateDTO(
        @Size(max = 150) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
        String name,
        @Size(max = 500)
        String description,
        @Positive
        BigDecimal price,
        @Positive
        Integer quantityTotal,
        @Positive
        Integer maxPerOrder
) {
    // blank description removes it, so it is kept as "" to tell it apart from null
    public TicketTypeUpdateDTO {
        if (description != null && description.isBlank()) description = "";
    }
}
