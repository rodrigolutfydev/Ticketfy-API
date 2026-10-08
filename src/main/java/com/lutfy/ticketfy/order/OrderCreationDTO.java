package com.lutfy.ticketfy.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreationDTO(
        @NotEmpty
        List<@Valid OrderItemRequestDTO> items,
        @Size(max = 100)
        String couponCode
) {
    public OrderCreationDTO(List<OrderItemRequestDTO> items) {
        this(items, null);
    }
}
