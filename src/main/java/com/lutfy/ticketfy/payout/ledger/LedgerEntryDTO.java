package com.lutfy.ticketfy.payout.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryDTO(
        UUID id,
        LedgerEntryType type,
        BigDecimal amount,
        UUID eventId,
        String eventName,
        UUID orderId,
        UUID payoutId,
        Instant createdAt
) {}
