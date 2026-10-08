package com.lutfy.ticketfy.payout.admin;

import com.lutfy.ticketfy.payout.account.PixKeyType;
import com.lutfy.ticketfy.payout.withdrawal.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminPayoutDTO(
        UUID id,
        UUID organizerId,
        String organizerName,
        String organizerEmail,
        BigDecimal amount,
        PayoutStatus status,
        PixKeyType pixKeyType,
        String pixKey,
        String holderName,
        Instant requestedAt,
        boolean organizerPayoutsBlocked
) {}
