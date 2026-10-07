package com.lutfy.ticketfy.payout;

import java.time.Instant;

public record PayoutAccountDTO(
        DocumentType documentType,
        String document,
        String holderName,
        PixKeyType pixKeyType,
        String pixKey,
        Instant payoutsBlockedUntil,
        Instant updatedAt
) {}
