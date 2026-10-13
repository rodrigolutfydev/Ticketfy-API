package com.lutfy.ticketfy.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationDTO(

        @NotBlank
        String name,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters long", max = 72)
        String password,

        @SupportedLanguage @Schema(allowableValues = {"pt-BR", "en"}, defaultValue = "pt-BR")
        String language
) {
    @Override
    public String toString() {
        return "UserRegistrationDTO[]";
    }
}
