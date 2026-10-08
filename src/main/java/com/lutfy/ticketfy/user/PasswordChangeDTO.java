package com.lutfy.ticketfy.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeDTO(
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long", max = 72) String newPassword
) {
    @Override
    public String toString() {
        return "PasswordChangeDTO[]";
    }
}
