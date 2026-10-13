package com.lutfy.ticketfy.infra.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class ExactTextValidator implements ConstraintValidator<ExactText, String> {

    private String expected;

    @Override
    public void initialize(ExactText annotation) {
        this.expected = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.equals(expected)) {
            return true;
        }
        ValidationPayload.attach(context, ValidationCodes.CONFIRMATION, Map.of("expected", expected));
        return false;
    }
}
