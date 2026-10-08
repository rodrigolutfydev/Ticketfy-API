package com.lutfy.ticketfy.payout.account;

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
