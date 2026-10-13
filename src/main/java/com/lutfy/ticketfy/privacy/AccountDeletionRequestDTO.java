package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.infra.validation.ExactText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequestDTO(
        @NotBlank @Size(max = 100) String password,
        @NotNull @ExactText(value = AccountDeletionRequestDTO.CONFIRMATION, message = "Type EXCLUIR to confirm")
        String confirmation
) {
    public static final String CONFIRMATION = "EXCLUIR";

    @Override
    public String toString() {
        return "AccountDeletionRequestDTO[]";
    }
}
