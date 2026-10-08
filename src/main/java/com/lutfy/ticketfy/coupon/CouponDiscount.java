package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponDiscount {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CouponDiscount() {
    }

    public static BigDecimal calculate(DiscountType type, BigDecimal value, BigDecimal subtotal) {
        var discount = switch (type) {
            case PERCENT -> subtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED -> value.setScale(2, RoundingMode.HALF_UP);
        };
        return discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
