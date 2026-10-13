package com.lutfy.ticketfy.infra.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.Map;

public class ExactTextValidator implements ConstraintValidator<ExactText, String> {

    private List<String> allowed;

    @Override
    public void initialize(ExactText annotation) {
        this.allowed = List.of(annotation.value());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || allowed.contains(value)) {
            return true;
        }
        ValidationPayload.attach(context, ValidationCodes.CONFIRMATION, Map.of("allowed", allowed));
        return false;
    }
}
