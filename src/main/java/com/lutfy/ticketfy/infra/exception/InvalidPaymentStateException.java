package com.lutfy.ticketfy.infra.exception;

public class InvalidPaymentStateException extends ReasonedException {
    public InvalidPaymentStateException(ProblemReason reason, String message) {
        super(reason, message);
    }
}
