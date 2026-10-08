package com.lutfy.ticketfy.payout.withdrawal;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.payout.admin.PayoutBlockRepository;
import com.lutfy.ticketfy.payout.ledger.LedgerService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PayoutTransitions {

    private final PayoutRepository repository;
    private final PayoutBlockRepository blockRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final PayoutSettings settings;
    private final Clock clock;

    public PayoutTransitions(PayoutRepository repository, PayoutBlockRepository blockRepository,
                             LedgerService ledgerService, AuditService auditService, PayoutSettings settings,
                             Clock clock) {
        this.repository = repository;
        this.blockRepository = blockRepository;
        this.auditService = auditService;
        this.ledgerService = ledgerService;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> requestedIds() {
        return repository.findProcessableIds();
    }

    @Transactional(readOnly = true)
    public List<UUID> stuckIds() {
        return repository.findStuckIds(clock.instant().minus(settings.stuckAfter()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PayoutGateway.TransferRequest> start(UUID payoutId) {
        var payout = repository.findById(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.REQUESTED
                || blockRepository.existsById(payout.getOrganizerId())) {
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
        var details = new HashMap<String, Object>();
        details.put("organizerId", payout.getOrganizerId());
        details.put("amount", payout.getAmount());
        switch (result.status()) {
            case COMPLETED -> payout.markPaid(result.reference(), clock.instant());
            case FAILED -> payout.markFailed(result.failureReason(), clock.instant());
            case PENDING -> {
                return payout.getStatus();
            }
        }
        repository.saveAndFlush(payout);
        if (payout.getStatus() == PayoutStatus.PAID) {
            details.put("transferReference", payout.getTransferReference());
            auditService.record(AuditAction.PAYOUT_PAID, AuditTargetType.PAYOUT, payout.getId(), details);
        } else {
            ledgerService.recordPayoutReversal(payout);
            details.put("failureReason", payout.getFailureReason());
            auditService.record(AuditAction.PAYOUT_FAILED, AuditTargetType.PAYOUT, payout.getId(), details);
        }
        return payout.getStatus();
    }

    private static PayoutGateway.TransferRequest transferRequest(Payout payout) {
        return new PayoutGateway.TransferRequest(payout.getId(), payout.getId().toString(), payout.getAmount(),
                payout.destination());
    }
}
