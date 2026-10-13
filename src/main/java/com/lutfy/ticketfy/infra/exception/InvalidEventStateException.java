package com.lutfy.ticketfy.infra.exception;

public class InvalidEventStateException extends ReasonedException {
    public InvalidEventStateException(ProblemReason reason, String message) {
        super(reason, message);
    }
}
