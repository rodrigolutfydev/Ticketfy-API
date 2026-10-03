package com.lutfy.ticketfy.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EventCreationDTO(
        @NotBlank @Size(max = 150)
        String name,
        String description,
        @NotBlank @Size(max = 150)
        String venueName,
        @NotBlank @Size(max = 255)
        String address,
        @NotBlank @Size(max = 100)
        String city,
        @NotBlank @Size(min = 2, max = 2)
        String state,

        @NotNull @Future
        Instant startsAt,
        @Future
        Instant endsAt
) {
}