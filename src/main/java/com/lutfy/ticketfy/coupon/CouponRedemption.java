package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponRedemption(UUID couponId, String code, DiscountType discountType, BigDecimal discountValue) {

    public BigDecimal discountFor(BigDecimal subtotal) {
        return CouponDiscount.calculate(discountType, discountValue, subtotal);
    }
}
