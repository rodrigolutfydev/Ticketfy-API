package com.lutfy.ticketfy.infra.exception;

public abstract class ReasonedException extends RuntimeException {

    private final ProblemReason reason;

    protected ReasonedException(String message) {
        this(null, message);
    }

    protected ReasonedException(ProblemReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ProblemReason getReason() {
        return reason;
    }
}
