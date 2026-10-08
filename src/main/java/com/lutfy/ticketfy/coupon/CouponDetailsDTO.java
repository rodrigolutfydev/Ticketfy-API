package com.lutfy.ticketfy.coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponDetailsDTO(
        UUID id,
        UUID eventId,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        Integer maxUses,
        int usesCount,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        boolean used,
        long paidOrders,
        BigDecimal discountTotal,
        Instant createdAt
) {
    public CouponDetailsDTO(Coupon coupon, long paidOrders, BigDecimal discountTotal) {
        this(coupon.getId(), coupon.getEvent().getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMaxUses(), coupon.getUsesCount(), coupon.getStartsAt(),
                coupon.getEndsAt(), coupon.getActive(), coupon.isUsed(), paidOrders, discountTotal,
                coupon.getCreatedAt());
    }
}
