package com.lutfy.ticketfy.event;

import jakarta.validation.constraints.Size;

public record EventCancellationDTO(
        @Size(max = 500) String reason
) {
}
