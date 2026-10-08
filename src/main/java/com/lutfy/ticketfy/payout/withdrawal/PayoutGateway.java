package com.lutfy.ticketfy.payout.withdrawal;

import com.lutfy.ticketfy.payout.account.PayoutDestination;
import com.lutfy.ticketfy.payout.account.PixKeyType;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PayoutGateway {

    Optional<String> lookupPixKeyHolder(PixKeyLookup lookup);

    TransferResult transfer(TransferRequest request);

    Optional<TransferResult> findTransfer(String idempotencyKey);

    record PixKeyLookup(PixKeyType type, String key, String requesterDocument) {
    }

    record TransferRequest(UUID payoutId, String idempotencyKey, BigDecimal amount, PayoutDestination destination) {
    }

    enum TransferStatus { COMPLETED, FAILED, PENDING }

    record TransferResult(TransferStatus status, String reference, String failureReason) {
    }
}
