package com.lutfy.ticketfy.payout.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidPayoutAccount
public record PayoutAccountUpdateDTO(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 25) String document,
        @NotBlank @Size(max = 150) String holderName,
        @NotNull PixKeyType pixKeyType,
        @NotBlank @Size(max = 100) String pixKey,
        @NotBlank @Size(max = 100) String password
) {
    @Override
    public String toString() {
        return "PayoutAccountUpdateDTO[documentType=" + documentType + ", pixKeyType=" + pixKeyType + "]";
    }
}
