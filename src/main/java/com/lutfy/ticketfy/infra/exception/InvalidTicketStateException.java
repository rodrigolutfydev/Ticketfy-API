package com.lutfy.ticketfy.infra.exception;

public class InvalidTicketStateException extends ReasonedException {
    public InvalidTicketStateException(ProblemReason reason, String message) {
        super(reason, message);
    }
}
