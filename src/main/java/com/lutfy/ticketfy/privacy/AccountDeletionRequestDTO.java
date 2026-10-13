package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.infra.validation.ExactText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequestDTO(
        @NotBlank @Size(max = 100) String password,
        @NotNull @ExactText(value = {AccountDeletionRequestDTO.CONFIRMATION_PT, AccountDeletionRequestDTO.CONFIRMATION_EN},
                message = "Type EXCLUIR or DELETE to confirm")
        @Schema(allowableValues = {AccountDeletionRequestDTO.CONFIRMATION_PT, AccountDeletionRequestDTO.CONFIRMATION_EN})
        String confirmation
) {
    public static final String CONFIRMATION_PT = "EXCLUIR";
    public static final String CONFIRMATION_EN = "DELETE";

    @Override
    public String toString() {
        return "AccountDeletionRequestDTO[]";
    }
}
