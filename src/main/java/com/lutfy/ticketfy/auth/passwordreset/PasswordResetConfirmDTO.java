package com.lutfy.ticketfy.auth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Mesmas regras de senha do cadastro (UserRegistrationDTO).
public record PasswordResetConfirmDTO(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long", max = 72) String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetConfirmDTO[]";
    }
}
