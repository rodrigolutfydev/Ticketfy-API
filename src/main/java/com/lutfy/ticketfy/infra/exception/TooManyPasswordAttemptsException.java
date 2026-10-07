package com.lutfy.ticketfy.infra.exception;

public class TooManyPasswordAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyPasswordAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
