package com.lutfy.ticketfy.event;

import jakarta.validation.constraints.NotNull;

public record EventFeaturedUpdateDTO(
        @NotNull
        Boolean featured
) {
}
