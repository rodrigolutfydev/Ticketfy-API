package com.lutfy.ticketfy.infra.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class NullOrNotBlankValidator implements ConstraintValidator<NullOrNotBlank, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || !value.isBlank()) {
            return true;
        }
        ValidationPayload.attach(context, ValidationCodes.REQUIRED, Map.of());
        return false;
    }
}
