package com.lutfy.ticketfy.infra.exception;

public class TooManyDataExportsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyDataExportsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
