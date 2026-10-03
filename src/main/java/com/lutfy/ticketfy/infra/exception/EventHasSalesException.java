package com.lutfy.ticketfy.infra.exception;

public class EventHasSalesException extends RuntimeException {
    public EventHasSalesException(String message) {
        super(message);
    }
}
