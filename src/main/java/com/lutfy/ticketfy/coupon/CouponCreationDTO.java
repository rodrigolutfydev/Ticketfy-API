package com.lutfy.ticketfy.coupon;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponCreationDTO(
        @NotNull @Pattern(regexp = "\\s*[A-Za-z0-9_-]{3,30}\\s*", message = "must have 3 to 30 letters, digits, '-' or '_'")
        String code,
        @NotNull
        DiscountType discountType,
        @NotNull @Positive @Digits(integer = 8, fraction = 2)
        BigDecimal discountValue,
        @Positive
        Integer maxUses,
        Instant startsAt,
        Instant endsAt,
        Boolean active
) {
    public CouponSettings settings() {
        return new CouponSettings(discountType, discountValue, maxUses, startsAt, endsAt, active == null || active);
    }
}
