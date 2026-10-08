package com.lutfy.ticketfy.payout.ledger;

import java.math.BigDecimal;

public record BalanceDTO(
        BigDecimal pending,
        BigDecimal available,
        BigDecimal inPayout,
        BigDecimal held,
        BigDecimal total,
        int releaseDelayDays,
        BigDecimal minPayoutAmount,
        boolean payoutsBlocked
) {}
