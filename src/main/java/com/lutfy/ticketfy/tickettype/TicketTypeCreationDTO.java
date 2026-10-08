package com.lutfy.ticketfy.tickettype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TicketTypeCreationDTO(
        @NotBlank @Size(max = 150)
        String name,
        @Size(max = 500)
        String description,
        @NotNull @PositiveOrZero
        BigDecimal price,
        @NotNull @Positive
        Integer quantityTotal,
        @Positive
        Integer maxPerOrder
) {

}
