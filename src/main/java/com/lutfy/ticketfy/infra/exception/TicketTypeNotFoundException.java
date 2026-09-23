package com.lutfy.ticketfy.infra.exception;

public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(String message) {
        super(message);
    }
}
