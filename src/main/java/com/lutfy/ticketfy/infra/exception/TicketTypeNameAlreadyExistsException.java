package com.lutfy.ticketfy.infra.exception;

public class TicketTypeNameAlreadyExistsException extends RuntimeException {
    public TicketTypeNameAlreadyExistsException(String message) {
        super(message);
    }
}
