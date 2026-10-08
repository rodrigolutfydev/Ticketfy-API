package com.lutfy.ticketfy.coupon;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponUpdateDTO(
        @NotNull
        DiscountType discountType,
        @NotNull @Positive @Digits(integer = 8, fraction = 2)
        BigDecimal discountValue,
        @Positive
        Integer maxUses,
        Instant startsAt,
        Instant endsAt,
        @NotNull
        Boolean active
) {
    public CouponSettings settings() {
        return new CouponSettings(discountType, discountValue, maxUses, startsAt, endsAt, active);
    }
}
