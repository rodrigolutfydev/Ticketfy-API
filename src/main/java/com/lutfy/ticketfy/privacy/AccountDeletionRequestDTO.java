package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.infra.validation.ExactText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Quem tem senha confirma com ela (password); conta sem senha (criada pelo
// Google) confirma digitando o próprio e-mail (email). A obrigatoriedade de
// cada um depende da conta e é conferida no AccountDeletionService.
public record AccountDeletionRequestDTO(
        @Size(max = 100) String password,
        @Size(max = 150) String email,
        @NotNull @ExactText(value = {AccountDeletionRequestDTO.CONFIRMATION_PT, AccountDeletionRequestDTO.CONFIRMATION_EN},
                message = "Type EXCLUIR or DELETE to confirm")
        @Schema(allowableValues = {AccountDeletionRequestDTO.CONFIRMATION_PT, AccountDeletionRequestDTO.CONFIRMATION_EN})
        String confirmation
) {
    public static final String CONFIRMATION_PT = "EXCLUIR";
    public static final String CONFIRMATION_EN = "DELETE";

    public AccountDeletionRequestDTO(String password, String confirmation) {
        this(password, null, confirmation);
    }

    @Override
    public String toString() {
        return "AccountDeletionRequestDTO[]";
    }
}
