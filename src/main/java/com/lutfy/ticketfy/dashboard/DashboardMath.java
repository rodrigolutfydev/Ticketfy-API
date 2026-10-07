package com.lutfy.ticketfy.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class DashboardMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DashboardMath() {
    }

    // Same scale as NUMERIC(10,2), so sums and empty results serialize like prices do
    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    // 0-100 with two decimals; null when there is nothing to compare against
    static BigDecimal percent(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal average(BigDecimal total, long count) {
        if (count == 0) return null;
        return money(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
