package com.lutfy.ticketfy.payout.withdrawal;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.payout.account.PayoutAccountRepository;
import com.lutfy.ticketfy.payout.admin.PayoutBlockRepository;
import com.lutfy.ticketfy.payout.ledger.LedgerQueryRepository;
import com.lutfy.ticketfy.payout.ledger.LedgerService;
import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayoutRequestService {

    private final PayoutRepository payoutRepository;
    private final PayoutAccountRepository accountRepository;
    private final PayoutBlockRepository blockRepository;
    private final AuditService auditService;
    private final LedgerQueryRepository queries;
    private final LedgerService ledgerService;
    private final PasswordConfirmation passwordConfirmation;
    private final PayoutSettings settings;
    private final Clock clock;

    public PayoutRequestService(PayoutRepository payoutRepository, PayoutAccountRepository accountRepository,
                                PayoutBlockRepository blockRepository, AuditService auditService,
                                LedgerQueryRepository queries, LedgerService ledgerService,
                                PasswordConfirmation passwordConfirmation, PayoutSettings settings, Clock clock) {
        this.payoutRepository = payoutRepository;
        this.accountRepository = accountRepository;
        this.blockRepository = blockRepository;
        this.auditService = auditService;
        this.queries = queries;
        this.ledgerService = ledgerService;
        this.passwordConfirmation = passwordConfirmation;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public PayoutDTO request(User organizer, PayoutRequestDTO dto, String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new ProblemException(ProblemType.INVALID_PARAMETER,
                    "Idempotency-Key must have between 1 and 100 characters");
        }
        passwordConfirmation.verify(organizer, dto.password());
        var account = accountRepository.findForUpdate(organizer.getId())
                .orElseThrow(() -> new ProblemException(ProblemType.PAYOUT_ACCOUNT_REQUIRED,
                        "Register your payout account before requesting a payout"));
        if (idempotencyKey != null) {
            var existing = payoutRepository.findByOrganizerIdAndIdempotencyKey(organizer.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return new PayoutDTO(existing.get());
            }
        }

        if (blockRepository.existsById(organizer.getId())) {
            throw new ProblemException(ProblemType.PAYOUTS_BLOCKED, "Payouts are blocked for this account");
        }
        var now = clock.instant();
        var blockedUntil = account.payoutsBlockedUntil(settings.keyChangeCooldown());
        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            throw new ProblemException(ProblemType.PAYOUT_ACCOUNT_COOLDOWN,
                    "Payouts are blocked after a pix key change", Map.of("blockedUntil", blockedUntil.toString()));
        }
        if (payoutRepository.existsByOrganizerIdAndStatusIn(organizer.getId(), PayoutStatus.inProgress())) {
            throw inProgress();
        }
        var available = queries.findBalance(organizer.getId(), now.minus(settings.releaseDelay())).available();
        if (available.signum() < 0) {
            throw new ProblemException(ProblemType.NEGATIVE_AVAILABLE_BALANCE,
                    "Available balance is negative", Map.of("available", available));
        }
        var amount = dto.amount().setScale(2);
        if (amount.compareTo(settings.minAmount()) < 0) {
            throw new ProblemException(ProblemType.PAYOUT_BELOW_MINIMUM,
                    "Minimum payout amount is " + settings.minAmount(), Map.of("minAmount", settings.minAmount()));
        }
        if (amount.compareTo(available) > 0) {
            throw new ProblemException(ProblemType.PAYOUT_EXCEEDS_AVAILABLE,
                    "Payout amount exceeds the available balance", Map.of("available", available));
        }

        var reviewReasons = new ArrayList<String>();
        if (!payoutRepository.existsByOrganizerIdAndStatus(organizer.getId(), PayoutStatus.PAID)) {
            reviewReasons.add("FIRST_PAYOUT");
        }
        if (amount.compareTo(settings.reviewThreshold()) > 0) {
            reviewReasons.add("ABOVE_THRESHOLD");
        }
        var status = reviewReasons.isEmpty() ? PayoutStatus.REQUESTED : PayoutStatus.UNDER_REVIEW;

        var payout = payoutRepository.saveAndFlush(
                new Payout(organizer.getId(), amount, idempotencyKey, account.destination(), status, now));
        ledgerService.recordPayoutDebit(payout);
        auditService.record(AuditAction.PAYOUT_REQUESTED, AuditTargetType.PAYOUT, payout.getId(),
                Map.of("organizerId", organizer.getId(), "amount", amount, "status", status));
        if (status == PayoutStatus.UNDER_REVIEW) {
            auditService.record(AuditAction.PAYOUT_SENT_TO_REVIEW, AuditTargetType.PAYOUT, payout.getId(),
                    Map.of("organizerId", organizer.getId(), "amount", amount, "reasons", reviewReasons,
                            "reviewThreshold", settings.reviewThreshold()));
        }
        return new PayoutDTO(payout);
    }

    @Transactional(readOnly = true)
    public Optional<PayoutDTO> findByIdempotencyKey(User organizer, String idempotencyKey) {
        return payoutRepository.findByOrganizerIdAndIdempotencyKey(organizer.getId(), idempotencyKey)
                .map(PayoutDTO::new);
    }

    @Transactional(readOnly = true)
    public boolean hasPayoutInProgress(User organizer) {
        return payoutRepository.existsByOrganizerIdAndStatusIn(organizer.getId(), PayoutStatus.inProgress());
    }

    @Transactional(readOnly = true)
    public Page<PayoutDTO> list(User organizer, Pageable pageable) {
        var sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
        return payoutRepository.findByOrganizerId(organizer.getId(), sorted).map(PayoutDTO::new);
    }

    @Transactional
    public PayoutDTO cancel(User organizer, UUID payoutId) {
        var payout = payoutRepository.findByIdAndOrganizerId(payoutId, organizer.getId())
                .orElseThrow(() -> new ProblemException(ProblemType.PAYOUT_NOT_FOUND, "Payout not found"));
        var previousStatus = payout.getStatus();
        payout.cancel(clock.instant());
        try {
            payoutRepository.saveAndFlush(payout);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ProblemException(ProblemType.INVALID_PAYOUT_STATE, "Payout is already being processed");
        }
        ledgerService.recordPayoutReversal(payout);
        auditService.record(AuditAction.PAYOUT_CANCELLED, AuditTargetType.PAYOUT, payout.getId(),
                Map.of("organizerId", organizer.getId(), "amount", payout.getAmount(), "previousStatus", previousStatus));
        return new PayoutDTO(payout);
    }

    static ProblemException inProgress() {
        return new ProblemException(ProblemType.PAYOUT_IN_PROGRESS, "There is already a payout in progress");
    }
}
