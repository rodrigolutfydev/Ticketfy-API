package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminPayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final Clock clock;

    public AdminPayoutService(PayoutRepository payoutRepository, PayoutBlockRepository blockRepository,
                              UserRepository userRepository, LedgerService ledgerService, AuditService auditService,
                              Clock clock) {
        this.payoutRepository = payoutRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AdminPayoutDTO> list(PayoutStatus status, Pageable pageable) {
        var page = payoutRepository.findByStatus(status, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"))));
        var organizerIds = page.getContent().stream().map(Payout::getOrganizerId).distinct().toList();
        var organizers = userRepository.findAllById(organizerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        var blocked = blockRepository.findAllById(organizerIds).stream()
                .map(PayoutBlock::getOrganizerId)
                .collect(Collectors.toSet());
        return page.map(payout -> {
            var organizer = organizers.get(payout.getOrganizerId());
            return new AdminPayoutDTO(payout.getId(), payout.getOrganizerId(),
                    organizer == null ? null : organizer.getName(), organizer == null ? null : organizer.getEmail(),
                    payout.getAmount(), payout.getStatus(), payout.getPixKeyType(),
                    Masking.pixKey(payout.getPixKeyType(), payout.getPixKey()), payout.getHolderName(),
                    payout.getRequestedAt(), blocked.contains(payout.getOrganizerId()));
        });
    }

    @Transactional
    public PayoutDTO approve(UUID payoutId, User admin) {
        var payout = findPayout(payoutId);
        payout.approve(admin.getId(), clock.instant());
        flush(payout);
        auditService.record(AuditAction.PAYOUT_APPROVED, AuditTargetType.PAYOUT, payout.getId(),
                Map.of("organizerId", payout.getOrganizerId(), "amount", payout.getAmount()));
        return new PayoutDTO(payout);
    }

    @Transactional
    public PayoutDTO reject(UUID payoutId, String reason, User admin) {
        var payout = findPayout(payoutId);
        var trimmed = reason.trim();
        payout.reject(admin.getId(), trimmed, clock.instant());
        flush(payout);
        ledgerService.recordPayoutReversal(payout);
        auditService.record(AuditAction.PAYOUT_REJECTED, AuditTargetType.PAYOUT, payout.getId(),
                Map.of("organizerId", payout.getOrganizerId(), "amount", payout.getAmount(), "reason", trimmed));
        return new PayoutDTO(payout);
    }

    @Transactional(readOnly = true)
    public OrganizerPayoutStatusDTO findOrganizer(String email) {
        var organizer = userRepository.findByEmail(email.trim())
                .filter(user -> user.getRole() == Role.ORGANIZER)
                .orElseThrow(AdminPayoutService::organizerNotFound);
        return status(organizer);
    }

    @Transactional
    public OrganizerPayoutStatusDTO block(UUID organizerId, String reason, User admin) {
        var organizer = findOrganizer(organizerId);
        if (blockRepository.existsById(organizerId)) {
            throw new ProblemException(ProblemType.INVALID_PAYOUT_BLOCK_STATE, "Payouts are already blocked");
        }
        var trimmed = reason.trim();
        blockRepository.saveAndFlush(new PayoutBlock(organizerId, trimmed, admin.getId(), clock.instant()));
        auditService.record(AuditAction.PAYOUTS_BLOCKED, AuditTargetType.ORGANIZER, organizerId,
                Map.of("reason", trimmed));
        return status(organizer);
    }

    @Transactional
    public OrganizerPayoutStatusDTO unblock(UUID organizerId) {
        var organizer = findOrganizer(organizerId);
        var block = blockRepository.findById(organizerId)
                .orElseThrow(() -> new ProblemException(ProblemType.INVALID_PAYOUT_BLOCK_STATE, "Payouts are not blocked"));
        blockRepository.delete(block);
        blockRepository.flush();
        auditService.record(AuditAction.PAYOUTS_UNBLOCKED, AuditTargetType.ORGANIZER, organizerId,
                Map.of("previousReason", block.getReason()));
        return status(organizer);
    }

    private OrganizerPayoutStatusDTO status(User organizer) {
        var block = blockRepository.findById(organizer.getId());
        return new OrganizerPayoutStatusDTO(organizer.getId(), organizer.getName(), organizer.getEmail(),
                block.isPresent(), block.map(PayoutBlock::getReason).orElse(null),
                block.map(PayoutBlock::getBlockedAt).orElse(null), block.map(PayoutBlock::getBlockedBy).orElse(null));
    }

    private User findOrganizer(UUID organizerId) {
        return userRepository.findById(organizerId)
                .filter(user -> user.getRole() == Role.ORGANIZER)
                .orElseThrow(AdminPayoutService::organizerNotFound);
    }

    private Payout findPayout(UUID payoutId) {
        return payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ProblemException(ProblemType.PAYOUT_NOT_FOUND, "Payout not found"));
    }

    private void flush(Payout payout) {
        try {
            payoutRepository.saveAndFlush(payout);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ProblemException(ProblemType.INVALID_PAYOUT_STATE, "Payout was changed by another action");
        }
    }

    private static ProblemException organizerNotFound() {
        return new ProblemException(ProblemType.ORGANIZER_NOT_FOUND, "Organizer not found");
    }
}
