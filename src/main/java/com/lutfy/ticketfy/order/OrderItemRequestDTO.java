package com.lutfy.ticketfy.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequestDTO(
        @NotNull
        UUID ticketTypeId,
        @NotNull @Positive
        Integer quantity
) {
}
