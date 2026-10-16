package com.lutfy.ticketfy.infra.exception;

/** 429 com {@code Retry-After} para um tipo de limite qualquer. */
public class TooManyRequestsException extends RuntimeException {

    private final ProblemType type;
    private final long retryAfterSeconds;

    public TooManyRequestsException(ProblemType type, String message, long retryAfterSeconds) {
        super(message);
        this.type = type;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ProblemType getType() {
        return type;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
