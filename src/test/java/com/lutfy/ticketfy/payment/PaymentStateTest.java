package com.lutfy.ticketfy.payment;

import com.lutfy.ticketfy.infra.exception.InvalidPaymentStateException;
import com.lutfy.ticketfy.infra.exception.ProblemReason;
import com.lutfy.ticketfy.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentStateTest {

    @Test
    void approvingOrRejectingAFinishedPaymentIsNotPending() {
        var payment = payment();
        payment.approve();

        assertThatThrownBy(payment::approve)
                .isInstanceOfSatisfying(InvalidPaymentStateException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ProblemReason.PAYMENT_NOT_PENDING));
        assertThatThrownBy(payment::reject)
                .isInstanceOfSatisfying(InvalidPaymentStateException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ProblemReason.PAYMENT_NOT_PENDING));
    }

    @Test
    void refundingAPaymentThatWasNotApprovedIsNotApproved() {
        var payment = payment();

        assertThatThrownBy(() -> payment.refund("ref"))
                .isInstanceOfSatisfying(InvalidPaymentStateException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ProblemReason.PAYMENT_NOT_APPROVED));
    }

    private static Payment payment() {
        var order = mock(Order.class);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("80.00"));
        return new Payment(order, PaymentMethod.SIMULATED);
    }
}
