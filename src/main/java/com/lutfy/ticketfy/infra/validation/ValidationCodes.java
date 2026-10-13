package com.lutfy.ticketfy.infra.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.engine.HibernateConstraintViolation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ValidationCodes {

    public static final String REQUIRED = "required";
    public static final String SIZE = "size";
    public static final String COUNT = "count";
    public static final String EMAIL = "email";
    public static final String POSITIVE = "positive";
    public static final String POSITIVE_OR_ZERO = "positive-or-zero";
    public static final String DIGITS = "digits";
    public static final String FUTURE = "future";
    public static final String URL_CODE = "url";
    public static final String ONE_OF = "one-of";
    public static final String COUPON_CODE = "coupon-code";
    public static final String CONFIRMATION = "confirmation";
    public static final String INVALID_DOCUMENT = "invalid-document";
    public static final String INVALID_PIX_KEY = "invalid-pix-key";
    public static final String INVALID = "invalid";

    private ValidationCodes() {
    }

    public static ValidationPayload of(ConstraintViolation<?> violation) {
        if (violation == null) {
            return new ValidationPayload(INVALID, Map.of());
        }
        var payload = dynamicPayload(violation);
        if (payload != null) {
            return payload;
        }
        var annotation = violation.getConstraintDescriptor().getAnnotation();
        var attributes = violation.getConstraintDescriptor().getAttributes();
        boolean collection = violation.getInvalidValue() instanceof Collection<?>;
        return fromAnnotation(annotation.annotationType(), attributes, collection);
    }

    private static ValidationPayload dynamicPayload(ConstraintViolation<?> violation) {
        try {
            HibernateConstraintViolation<?> hibernateViolation = violation.unwrap(HibernateConstraintViolation.class);
            return hibernateViolation.getDynamicPayload(ValidationPayload.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ValidationPayload fromAnnotation(Class<? extends Annotation> type, Map<String, Object> attributes,
                                                    boolean collection) {
        if (type == NotBlank.class || type == NotNull.class) {
            return code(REQUIRED);
        }
        if (type == NotEmpty.class) {
            return collection ? new ValidationPayload(COUNT, Map.of("min", 1)) : code(REQUIRED);
        }
        if (type == Size.class) {
            var params = new LinkedHashMap<String, Object>();
            int min = (Integer) attributes.get("min");
            int max = (Integer) attributes.get("max");
            if (min > 0) params.put("min", min);
            if (max != Integer.MAX_VALUE) params.put("max", max);
            return new ValidationPayload(collection ? COUNT : SIZE, params);
        }
        if (type == Email.class) {
            return code(EMAIL);
        }
        if (type == Positive.class) {
            return code(POSITIVE);
        }
        if (type == PositiveOrZero.class) {
            return code(POSITIVE_OR_ZERO);
        }
        if (type == Digits.class) {
            return new ValidationPayload(DIGITS,
                    Map.of("integer", attributes.get("integer"), "fraction", attributes.get("fraction")));
        }
        if (type == Future.class) {
            return code(FUTURE);
        }
        if (type == URL.class) {
            var protocol = (String) attributes.get("protocol");
            return protocol == null || protocol.isBlank()
                    ? code(URL_CODE)
                    : new ValidationPayload(URL_CODE, Map.of("protocol", protocol));
        }
        return code(INVALID);
    }

    private static ValidationPayload code(String code) {
        return new ValidationPayload(code, Map.of());
    }
}
