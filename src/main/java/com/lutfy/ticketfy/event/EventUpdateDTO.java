package com.lutfy.ticketfy.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record EventUpdateDTO(
        @Size(max = 150)
        String name,
        String description,
        @Size(max = 500) @URL(protocol = "https")
        String imageUrl,
        @Size(max = 150)
        String venueName,
        @Size(max = 255)
        String address,
        @Size(max = 100)
        String city,
        @Size(min = 2, max = 2)
        String state,

        @Future
        Instant startsAt,
        @Future
        Instant endsAt
    ) {
    // null keeps the current image and blank removes it, so blank is kept as "" to tell both apart
    public EventUpdateDTO {
        if (imageUrl != null && imageUrl.isBlank()) imageUrl = "";
    }
}
