package com.lutfy.ticketfy.infra.exception;

public class MaxPerOrderExceededException extends RuntimeException {
    public MaxPerOrderExceededException(String message) {
        super(message);
    }
}
