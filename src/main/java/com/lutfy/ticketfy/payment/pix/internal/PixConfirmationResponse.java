package com.lutfy.ticketfy.payment.pix.internal;

import java.util.UUID;

public record PixConfirmationResponse(
        UUID paymentId,
        UUID orderId,
        String status,
        boolean alreadyProcessed
) {}
