package com.lutfy.ticketfy.tickettype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TicketTypeCreationData(
        @NotBlank @Size(max = 150)
        String name,
        @Size(max = 445)
        String description,
        @NotNull @Positive
        BigDecimal price,
        @NotNull @Positive
        Integer quantityTotal,
        @Positive
        Integer maxPerOrder
) {

}
