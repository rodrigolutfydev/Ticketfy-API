package com.lutfy.ticketfy.payout;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PayoutTransitions {

    private final PayoutRepository repository;
    private final LedgerService ledgerService;
    private final PayoutSettings settings;
    private final Clock clock;

    public PayoutTransitions(PayoutRepository repository, LedgerService ledgerService, PayoutSettings settings,
                             Clock clock) {
        this.repository = repository;
        this.ledgerService = ledgerService;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> requestedIds() {
        return repository.findIdsByStatus(PayoutStatus.REQUESTED);
    }

    @Transactional(readOnly = true)
    public List<UUID> stuckIds() {
        return repository.findStuckIds(clock.instant().minus(settings.stuckAfter()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PayoutGateway.TransferRequest> start(UUID payoutId) {
        var payout = repository.findById(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.REQUESTED) {
            return Optional.empty();
        }
        payout.startProcessing(clock.instant());
        try {
            repository.saveAndFlush(payout);
        } catch (ObjectOptimisticLockingFailureException ex) {
            return Optional.empty();
        }
        return Optional.of(transferRequest(payout));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<PayoutGateway.TransferRequest> resumable(UUID payoutId) {
        return repository.findById(payoutId)
                .filter(payout -> payout.getStatus() == PayoutStatus.PROCESSING)
                .map(PayoutTransitions::transferRequest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutStatus finish(UUID payoutId, PayoutGateway.TransferResult result) {
        var payout = repository.findById(payoutId).orElseThrow();
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            return payout.getStatus();
        }
        switch (result.status()) {
            case COMPLETED -> payout.markPaid(result.reference(), clock.instant());
            case FAILED -> {
                payout.markFailed(result.failureReason(), clock.instant());
                ledgerService.recordPayoutReversal(payout);
            }
            case PENDING -> {
                return payout.getStatus();
            }
        }
        repository.saveAndFlush(payout);
        return payout.getStatus();
    }

    private static PayoutGateway.TransferRequest transferRequest(Payout payout) {
        return new PayoutGateway.TransferRequest(payout.getId(), payout.getId().toString(), payout.getAmount(),
                payout.destination());
    }
}
