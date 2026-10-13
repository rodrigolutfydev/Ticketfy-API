package com.lutfy.ticketfy.coupon;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CouponCodeFormatValidator.class)
public @interface CouponCodeFormat {
    String message() default "must have 3 to 30 letters, digits, '-' or '_'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
