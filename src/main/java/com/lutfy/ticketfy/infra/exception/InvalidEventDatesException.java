package com.lutfy.ticketfy.infra.exception;

public class InvalidEventDatesException extends RuntimeException {
    public InvalidEventDatesException(String message) {
        super(message);
    }
}
