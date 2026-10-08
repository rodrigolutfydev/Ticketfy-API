package com.lutfy.ticketfy.payout;

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
