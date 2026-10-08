package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;

public record CouponPreviewDTO(
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total
) {
}
