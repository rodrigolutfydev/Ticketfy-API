package com.lutfy.ticketfy.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    RefundResult refund(RefundRequest request);

    record RefundRequest(UUID paymentId, String providerReference, BigDecimal amount, String idempotencyKey) {
    }

    record RefundResult(String reference) {
    }
}
