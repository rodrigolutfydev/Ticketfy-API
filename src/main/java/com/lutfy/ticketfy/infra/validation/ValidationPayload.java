package com.lutfy.ticketfy.infra.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import java.util.Map;

public record ValidationPayload(String code, Map<String, Object> params) {

    public static void attach(ConstraintValidatorContext context, String code, Map<String, Object> params) {
        context.unwrap(HibernateConstraintValidatorContext.class)
                .withDynamicPayload(new ValidationPayload(code, params));
    }
}
