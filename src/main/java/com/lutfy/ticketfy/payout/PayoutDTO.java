package com.lutfy.ticketfy.payout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutDTO(
        UUID id,
        BigDecimal amount,
        PayoutStatus status,
        PixKeyType pixKeyType,
        String pixKey,
        String holderName,
        Instant requestedAt,
        Instant processingStartedAt,
        Instant finishedAt,
        String failureReason
) {
    public PayoutDTO(Payout payout) {
        this(payout.getId(), payout.getAmount(), payout.getStatus(), payout.getPixKeyType(),
                Masking.pixKey(payout.getPixKeyType(), payout.getPixKey()), payout.getHolderName(),
                payout.getRequestedAt(), payout.getProcessingStartedAt(), payout.getFinishedAt(),
                payout.getFailureReason());
    }
}
