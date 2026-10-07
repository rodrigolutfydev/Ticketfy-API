package com.lutfy.ticketfy.infra.exception;

public class MixedEventsOrderException extends RuntimeException {
    public MixedEventsOrderException(String message) {
        super(message);
    }
}
