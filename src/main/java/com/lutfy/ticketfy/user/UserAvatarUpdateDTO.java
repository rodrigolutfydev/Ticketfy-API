package com.lutfy.ticketfy.user;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UserAvatarUpdateDTO(
        @Size(max = 500) @URL(protocol = "https")
        String avatarUrl
) {
    public UserAvatarUpdateDTO {
        if (avatarUrl != null && avatarUrl.isBlank()) avatarUrl = null;
    }
}
