package com.lutfy.ticketfy.infra.exception;

public class TooManyTransferAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyTransferAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
