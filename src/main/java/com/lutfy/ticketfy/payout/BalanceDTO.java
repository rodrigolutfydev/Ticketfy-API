package com.lutfy.ticketfy.payout;

import java.math.BigDecimal;

public record BalanceDTO(
        BigDecimal pending,
        BigDecimal available,
        BigDecimal held,
        BigDecimal total,
        int releaseDelayDays
) {}
