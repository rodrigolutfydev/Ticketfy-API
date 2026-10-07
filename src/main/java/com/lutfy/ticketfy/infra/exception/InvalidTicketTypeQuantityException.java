package com.lutfy.ticketfy.infra.exception;

public class InvalidTicketTypeQuantityException extends RuntimeException {
    public InvalidTicketTypeQuantityException(String message) {
        super(message);
    }
}
