package com.lutfy.ticketfy.payout.account;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PayoutAccountValidator.class)
public @interface ValidPayoutAccount {
    String message() default "Invalid payout account";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
