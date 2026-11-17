package com.lutfy.ticketfy.payment.pix.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PixConfirmationRequest(
        @NotNull UUID paymentId,
        @NotNull UUID orderId,
        @Positive long amountCents
) {}
