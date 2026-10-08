package com.lutfy.ticketfy.coupon;

import java.util.Locale;

public final class CouponCodes {

    private CouponCodes() {
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) return null;
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
