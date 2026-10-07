package com.lutfy.ticketfy.infra.exception;

import java.util.Map;

public class ProblemException extends RuntimeException {

    private final ProblemType type;
    private final Map<String, Object> properties;

    public ProblemException(ProblemType type, String message) {
        this(type, message, Map.of());
    }

    public ProblemException(ProblemType type, String message, Map<String, Object> properties) {
        super(message);
        this.type = type;
        this.properties = properties;
    }

    public ProblemType getType() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
