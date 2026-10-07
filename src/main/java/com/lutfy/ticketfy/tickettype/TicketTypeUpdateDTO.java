package com.lutfy.ticketfy.tickettype;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
    public TicketTypeUpdateDTO {
        if (description != null && description.isBlank()) description = "";
    }
}
