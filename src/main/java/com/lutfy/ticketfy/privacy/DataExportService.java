package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.infra.exception.TooManyDataExportsException;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import com.lutfy.ticketfy.payout.account.PayoutAccountRepository;
import com.lutfy.ticketfy.payout.ledger.BalanceService;
import com.lutfy.ticketfy.payout.ledger.LedgerQueryRepository;
import com.lutfy.ticketfy.payout.withdrawal.PayoutRepository;
import com.lutfy.ticketfy.privacy.DataExportDTO.Organizer;
import com.lutfy.ticketfy.privacy.DataExportDTO.PayoutAccountEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.PayoutEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.Profile;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserLocks;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DataExportService {

    private final UserRepository userRepository;
    private final DataExportQueryRepository queries;
    private final BalanceService balanceService;
    private final LedgerQueryRepository ledgerQueries;
    private final PayoutRepository payoutRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final PasswordConfirmation passwordConfirmation;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId zone;
    private final int maxExports;
    private final Duration window;

    public DataExportService(UserRepository userRepository, DataExportQueryRepository queries,
                             BalanceService balanceService, LedgerQueryRepository ledgerQueries,
                             PayoutRepository payoutRepository, PayoutAccountRepository payoutAccountRepository,
                             PasswordConfirmation passwordConfirmation, AuditService auditService, Clock clock,
                             @Value("${ticketfy.dashboard.time-zone}") String timeZone,
                             @Value("${ticketfy.privacy.export.max-per-window}") int maxExports,
                             @Value("${ticketfy.privacy.export.window-hours}") long windowHours) {
        this.userRepository = userRepository;
        this.queries = queries;
        this.balanceService = balanceService;
        this.ledgerQueries = ledgerQueries;
        this.payoutRepository = payoutRepository;
        this.payoutAccountRepository = payoutAccountRepository;
        this.passwordConfirmation = passwordConfirmation;
        this.auditService = auditService;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
        this.maxExports = maxExports;
        this.window = Duration.ofHours(windowHours);
    }

    @Transactional
    public DataExportDTO export(User principal, DataExportRequestDTO dto) {
        passwordConfirmation.verify(principal, dto.password());
        var user = userRepository.findForUpdate(principal.getId())
                .filter(found -> !found.isDeleted())
                .orElseThrow(UserLocks::accountDeleted);
        var now = clock.instant();
        var recent = queries.findRecentExports(user.getId(), now.minus(window));
        if (recent.size() >= maxExports) {
            var retryAfter = Math.max(1, Duration.between(now, recent.get(0).plus(window)).toSeconds());
            throw new TooManyDataExportsException("Data export limit reached. Try again later.", retryAfter);
        }

        var export = new DataExportDTO(DataExportDTO.SCHEMA_VERSION, now,
                new Profile(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAvatarUrl(),
                        user.getCreatedAt(), user.getUpdatedAt()),
                queries.findOrders(user.getId()),
                queries.findOwnedTickets(user.getId()),
                queries.findTransfers(user.getId()),
                organizer(user));
        auditService.record(AuditAction.DATA_EXPORTED, AuditTargetType.USER, user.getId(), counts(export));
        return export;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private Organizer organizer(User user) {
        if (user.getRole() != Role.ORGANIZER && !queries.hasEvents(user.getId())) {
            return null;
        }
        var payouts = payoutRepository.findByOrganizerId(user.getId(),
                        Pageable.unpaged(Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("id"))))
                .map(payout -> new PayoutEntry(payout.getId(), payout.getAmount(), payout.getStatus().name(),
                        payout.getDocumentType(), payout.getDocument(), payout.getHolderName(),
                        payout.getPixKeyType(), payout.getPixKey(), payout.getRequestedAt(),
                        payout.getProcessingStartedAt(), payout.getFinishedAt(), payout.getFailureReason(),
                        payout.getRejectionReason()))
                .getContent();
        var account = payoutAccountRepository.findById(user.getId())
                .map(found -> new PayoutAccountEntry(found.getDocumentType(), found.getDocument(),
                        found.getHolderName(), found.getPixKeyType(), found.getPixKey(), found.getCreatedAt(),
                        found.getUpdatedAt()))
                .orElse(null);
        return new Organizer(queries.findEvents(user.getId()), balanceService.balance(user),
                ledgerQueries.findAllEntries(user.getId(), null, null, null), payouts, account);
    }

    private static Map<String, Integer> counts(DataExportDTO export) {
        var counts = new LinkedHashMap<String, Integer>();
        counts.put("orders", export.orders().size());
        counts.put("tickets", export.tickets().size());
        counts.put("transfers", export.transfers().size());
        var organizer = export.organizer();
        counts.put("events", organizer == null ? 0 : organizer.events().size());
        counts.put("ledgerEntries", organizer == null ? 0 : organizer.ledger().size());
        counts.put("payouts", organizer == null ? 0 : organizer.payouts().size());
        counts.put("payoutAccount", organizer == null || organizer.payoutAccount() == null ? 0 : 1);
        return counts;
    }
}
