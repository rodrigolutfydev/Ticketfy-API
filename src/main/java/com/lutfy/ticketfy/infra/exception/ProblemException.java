package com.lutfy.ticketfy.infra.exception;

import com.lutfy.ticketfy.infra.validation.ValidationCodes;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 400 validation-failed para um campo que só é obrigatório conforme a conta
     * (por exemplo, a senha atual de quem tem senha), no mesmo formato da
     * validação do corpo.
     */
    public static ProblemException requiredField(String field) {
        var error = new ValidationErrorDTO(field, "must not be blank", ValidationCodes.REQUIRED, Map.of());
        return new ProblemException(ProblemType.VALIDATION_FAILED, "Validation failed", Map.of("errors", List.of(error)));
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
