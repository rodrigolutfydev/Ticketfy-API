package com.lutfy.ticketfy.infra.exception;

public class EventAccessDeniedException extends RuntimeException {
    public EventAccessDeniedException(String message) {
        super(message);
    }
}
