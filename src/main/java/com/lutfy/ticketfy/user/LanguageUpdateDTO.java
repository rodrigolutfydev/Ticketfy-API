package com.lutfy.ticketfy.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LanguageUpdateDTO(
        @NotNull @SupportedLanguage @Schema(allowableValues = {"pt-BR", "en"})
        String language
) {
}
