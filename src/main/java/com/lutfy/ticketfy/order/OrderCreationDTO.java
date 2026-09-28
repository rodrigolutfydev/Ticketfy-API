package com.lutfy.ticketfy.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderCreationDTO(
        @NotEmpty @Valid
        List<@Valid OrderItemRequestDTO> items
) {}
