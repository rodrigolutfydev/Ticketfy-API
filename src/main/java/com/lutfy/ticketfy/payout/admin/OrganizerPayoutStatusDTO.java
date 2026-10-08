package com.lutfy.ticketfy.payout.admin;

import java.time.Instant;
import java.util.UUID;

public record OrganizerPayoutStatusDTO(
        UUID id,
        String name,
        String email,
        boolean payoutsBlocked,
        String blockReason,
        Instant blockedAt,
        UUID blockedBy
) {}
