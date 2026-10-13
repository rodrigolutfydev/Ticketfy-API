package com.lutfy.ticketfy.infra.exception;

import java.util.LinkedHashMap;
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

    public ProblemException(ProblemReason reason, String message) {
        this(reason, message, Map.of());
    }

    public ProblemException(ProblemReason reason, String message, Map<String, Object> properties) {
        this(reason.type(), message, withReason(reason, properties));
    }

    public ProblemType getType() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    private static Map<String, Object> withReason(ProblemReason reason, Map<String, Object> properties) {
        var all = new LinkedHashMap<String, Object>();
        all.put(ProblemReason.PROPERTY, reason.value());
        all.putAll(properties);
        return all;
    }
}
