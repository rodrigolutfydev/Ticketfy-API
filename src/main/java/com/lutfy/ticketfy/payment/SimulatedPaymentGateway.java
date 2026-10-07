package com.lutfy.ticketfy.payment;

import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public RefundResult refund(RefundRequest request) {
        return new RefundResult("SIM-REFUND-" + request.idempotencyKey());
    }
}
