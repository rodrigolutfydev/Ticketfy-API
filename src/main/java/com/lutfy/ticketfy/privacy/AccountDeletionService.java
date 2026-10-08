package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.payout.account.PayoutAccountRepository;
import com.lutfy.ticketfy.payout.admin.PayoutBlockRepository;
import com.lutfy.ticketfy.payout.ledger.LedgerQueryRepository;
import com.lutfy.ticketfy.payout.withdrawal.PayoutRepository;
import com.lutfy.ticketfy.payout.withdrawal.PayoutStatus;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserLocks;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AccountDeletionService {

    private static final int SECRET_BYTES = 32;

    private final UserRepository userRepository;
    private final AccountDeletionQueryRepository queries;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EventRepository eventRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final PayoutRepository payoutRepository;
    private final PayoutBlockRepository payoutBlockRepository;
    private final LedgerQueryRepository ledgerQueries;
    private final PayoutSettings payoutSettings;
    private final AuthSessionService authSessions;
    private final PasswordConfirmation passwordConfirmation;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountDeletionService(UserRepository userRepository, AccountDeletionQueryRepository queries,
                                  OrderRepository orderRepository, OrderService orderService,
                                  EventRepository eventRepository, PayoutAccountRepository payoutAccountRepository,
                                  PayoutRepository payoutRepository, PayoutBlockRepository payoutBlockRepository,
                                  LedgerQueryRepository ledgerQueries, PayoutSettings payoutSettings,
                                  AuthSessionService authSessions, PasswordConfirmation passwordConfirmation,
                                  PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.queries = queries;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.eventRepository = eventRepository;
        this.payoutAccountRepository = payoutAccountRepository;
        this.payoutRepository = payoutRepository;
        this.payoutBlockRepository = payoutBlockRepository;
        this.ledgerQueries = ledgerQueries;
        this.payoutSettings = payoutSettings;
        this.authSessions = authSessions;
        this.passwordConfirmation = passwordConfirmation;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void delete(User principal, AccountDeletionRequestDTO dto) {
        if (principal.getRole() == Role.ADMIN) {
            throw new ProblemException(ProblemType.ADMIN_ACCOUNT_DELETION, "Admin accounts cannot be deleted");
        }
        passwordConfirmation.verify(principal, dto.password());
        var user = userRepository.findForUpdate(principal.getId())
                .filter(found -> !found.isDeleted())
                .orElseThrow(UserLocks::accountDeleted);
        var pendingOrderIds = queries.lockPendingOrders(user.getId());
        var account = payoutAccountRepository.findForUpdate(user.getId());
        queries.lockTicketTypesOfOrganizer(user.getId());
        var now = clock.instant();

        checkCanDelete(user, now);

        for (var orderId : pendingOrderIds) {
            orderService.cancelPending(orderRepository.findById(orderId).orElseThrow());
        }
        int deactivatedEvents = eventRepository.deactivateAllByOrganizer(user.getId(), now);
        account.ifPresent(payoutAccountRepository::delete);
        int revokedSessions = authSessions.revokeAll(user.getId(), RevocationReason.ACCOUNT_DELETED);
        user.anonymize(passwordEncoder.encode(randomSecret()), now);
        userRepository.flush();

        var details = new LinkedHashMap<String, Object>();
        details.put("revokedSessions", revokedSessions);
        details.put("cancelledPendingOrders", pendingOrderIds.size());
        details.put("deactivatedEvents", deactivatedEvents);
        details.put("payoutAccountRemoved", account.isPresent());
        auditService.record(AuditAction.ACCOUNT_DELETED, AuditTargetType.USER, user.getId(), details);
    }

    private void checkCanDelete(User user, Instant now) {
        var userId = user.getId();
        if (payoutBlockRepository.existsById(userId)) {
            throw new ProblemException(ProblemType.ACCOUNT_HAS_PAYOUT_BLOCK,
                    "Payouts are blocked for this account. Contact support to delete it.");
        }
        if (payoutRepository.existsByOrganizerIdAndStatusIn(userId, PayoutStatus.inProgress())) {
            throw new ProblemException(ProblemType.ACCOUNT_HAS_PAYOUT_IN_PROGRESS,
                    "Wait for the payout in progress to finish, or cancel it, before deleting the account");
        }
        var balance = ledgerQueries.findBalance(userId, now.minus(payoutSettings.releaseDelay()));
        if (nonZero(balance.pending()) || nonZero(balance.available()) || nonZero(balance.held())) {
            throw new ProblemException(ProblemType.ACCOUNT_HAS_BALANCE,
                    "The organizer balance must be zero before deleting the account",
                    Map.of("pending", money(balance.pending()), "available", money(balance.available()),
                            "held", money(balance.held())));
        }
        var activeEvents = queries.findEventsWithActiveSales(userId, now);
        if (!activeEvents.isEmpty()) {
            throw new ProblemException(ProblemType.ACCOUNT_HAS_ACTIVE_EVENTS,
                    "Cancel or finish the events with sales before deleting the account",
                    Map.of("eventIds", activeEvents));
        }
        int upcomingTickets = queries.countUpcomingTickets(userId, now);
        if (upcomingTickets > 0) {
            throw new ProblemException(ProblemType.ACCOUNT_HAS_UPCOMING_TICKETS,
                    "Transfer or refund the valid tickets for upcoming events before deleting the account",
                    Map.of("ticketCount", upcomingTickets));
        }
    }

    private String randomSecret() {
        var bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean nonZero(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
