package com.lutfy.ticketfy.payment.pix;

import java.util.UUID;

public record PixPaymentResponse(
        UUID orderId,
        UUID paymentId,
        String pixCopyPaste,
        String pixQrCode,
        String status
) {}