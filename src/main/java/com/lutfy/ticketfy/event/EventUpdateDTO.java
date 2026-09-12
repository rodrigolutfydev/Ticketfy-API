package com.lutfy.ticketfy.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventUpdateDTO(
        @Size(max = 150)
        String name,
        String description,
        @Size(max = 150)
        String venueName,
        @Size(max = 255)
        String address,
        @Size(max = 100)
        String city,
        @Size(min = 2, max = 2)
        String state,

        @Future
        LocalDateTime startsAt,
        @Future
        LocalDateTime endsAt
    ) {
}
