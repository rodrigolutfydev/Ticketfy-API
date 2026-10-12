package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponRedemption(UUID couponId, String code, DiscountType discountType, BigDecimal discountValue,
                               Integer maxUsesPerBuyer) {

    public BigDecimal discountFor(BigDecimal subtotal) {
        return CouponDiscount.calculate(discountType, discountValue, subtotal);
    }
}
