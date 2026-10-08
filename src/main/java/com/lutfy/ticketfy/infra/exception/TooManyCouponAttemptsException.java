package com.lutfy.ticketfy.infra.exception;

public class TooManyCouponAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyCouponAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
