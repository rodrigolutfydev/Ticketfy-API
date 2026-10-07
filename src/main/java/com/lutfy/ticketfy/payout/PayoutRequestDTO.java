package com.lutfy.ticketfy.payout;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayoutRequestDTO(
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 100) String password
) {
    @Override
    public String toString() {
        return "PayoutRequestDTO[amount=" + amount + "]";
    }
}
