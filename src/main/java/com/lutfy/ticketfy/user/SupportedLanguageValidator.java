package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.validation.ValidationCodes;
import com.lutfy.ticketfy.infra.validation.ValidationPayload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class SupportedLanguageValidator implements ConstraintValidator<SupportedLanguage, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || Language.fromCode(value).isPresent()) {
            return true;
        }
        ValidationPayload.attach(context, ValidationCodes.ONE_OF, Map.of("allowed", Language.codes()));
        return false;
    }
}
