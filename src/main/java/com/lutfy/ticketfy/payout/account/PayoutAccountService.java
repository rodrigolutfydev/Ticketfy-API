package com.lutfy.ticketfy.payout.account;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.payout.withdrawal.PayoutGateway;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserLocks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Service
public class PayoutAccountService {

    private final PayoutAccountRepository repository;
    private final PayoutGateway gateway;
    private final PasswordConfirmation passwordConfirmation;
    private final PayoutSettings settings;
    private final AuditService auditService;
    private final Clock clock;
    private final UserLocks userLocks;

    public PayoutAccountService(PayoutAccountRepository repository, PayoutGateway gateway,
                                PasswordConfirmation passwordConfirmation, PayoutSettings settings,
                                AuditService auditService, Clock clock, UserLocks userLocks) {
        this.repository = repository;
        this.gateway = gateway;
        this.passwordConfirmation = passwordConfirmation;
        this.settings = settings;
        this.auditService = auditService;
        this.clock = clock;
        this.userLocks = userLocks;
    }

    @Transactional(readOnly = true)
    public PayoutAccountDTO find(User organizer) {
        return repository.findById(organizer.getId())
                .map(this::toDTO)
                .orElseThrow(() -> new ProblemException(ProblemType.PAYOUT_ACCOUNT_NOT_FOUND, "Payout account not found"));
    }

    @Transactional
    public PayoutAccountDTO save(User organizer, PayoutAccountUpdateDTO dto) {
        passwordConfirmation.verify(organizer, dto.password());
        var destination = normalize(dto);
        var holder = gateway.lookupPixKeyHolder(new PayoutGateway.PixKeyLookup(
                        destination.pixKeyType(), destination.pixKey(), destination.document()))
                .map(BrazilianDocuments::normalize)
                .orElseThrow(() -> new ProblemException(ProblemType.PIX_KEY_NOT_FOUND, "Pix key not found"));
        if (!holder.equals(destination.document())) {
            throw new ProblemException(ProblemType.PIX_KEY_HOLDER_MISMATCH,
                    "The pix key does not belong to the informed document");
        }
        userLocks.requireActive(organizer.getId());
        var existing = repository.findForUpdate(organizer.getId());
        boolean keyChanged = existing.map(account -> account.update(destination, clock.instant())).orElse(false);
        var saved = repository.saveAndFlush(existing.orElseGet(() -> new PayoutAccount(organizer.getId(), destination)));
        auditService.record(AuditAction.PAYOUT_ACCOUNT_SAVED, AuditTargetType.PAYOUT_ACCOUNT, organizer.getId(),
                Map.of("created", existing.isEmpty(), "keyChanged", keyChanged,
                        "documentType", destination.documentType(), "pixKeyType", destination.pixKeyType()));
        return toDTO(saved);
    }

    private PayoutAccountDTO toDTO(PayoutAccount account) {
        var blockedUntil = account.payoutsBlockedUntil(settings.keyChangeCooldown());
        if (blockedUntil != null && !blockedUntil.isAfter(clock.instant())) {
            blockedUntil = null;
        }
        return new PayoutAccountDTO(account.getDocumentType(),
                Masking.document(account.getDocumentType(), account.getDocument()),
                account.getHolderName(), account.getPixKeyType(),
                Masking.pixKey(account.getPixKeyType(), account.getPixKey()),
                blockedUntil, account.getUpdatedAt());
    }

    private static PayoutDestination normalize(PayoutAccountUpdateDTO dto) {
        var document = BrazilianDocuments.normalize(dto.document());
        var pixKey = PixKeys.normalize(dto.pixKeyType(), dto.pixKey())
                .orElseThrow(() -> new IllegalArgumentException("Pix key was not validated"));
        return new PayoutDestination(dto.documentType(), document, dto.holderName().trim(), dto.pixKeyType(), pixKey);
    }
}
