package com.lutfy.ticketfy.infra.exception;

public class InvalidOrderStateException extends ReasonedException {
    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(ProblemReason reason, String message) {
        super(reason, message);
    }
}
