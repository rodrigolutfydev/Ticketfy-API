package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.validation.ValidationCodes;
import com.lutfy.ticketfy.infra.validation.ValidationPayload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.regex.Pattern;

public class CouponCodeFormatValidator implements ConstraintValidator<CouponCodeFormat, String> {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 30;
    private static final Pattern FORMAT = Pattern.compile("\\s*[A-Za-z0-9_-]{" + MIN_LENGTH + "," + MAX_LENGTH + "}\\s*");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || FORMAT.matcher(value).matches()) {
            return true;
        }
        ValidationPayload.attach(context, ValidationCodes.COUPON_CODE, Map.of("min", MIN_LENGTH, "max", MAX_LENGTH));
        return false;
    }
}
