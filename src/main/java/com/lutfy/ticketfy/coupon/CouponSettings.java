package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponSettings(
        DiscountType discountType,
        BigDecimal discountValue,
        Integer maxUses,
        Instant startsAt,
        Instant endsAt,
        boolean active
) {
}
