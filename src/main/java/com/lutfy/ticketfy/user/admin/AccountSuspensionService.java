package com.lutfy.ticketfy.user.admin;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemReason;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payout.admin.PayoutBlockRepository;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class AccountSuspensionService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PayoutBlockRepository payoutBlockRepository;
    private final AuthSessionService authSessions;
    private final AuditService auditService;
    private final Clock clock;

    public AccountSuspensionService(UserRepository userRepository, OrderRepository orderRepository,
                                    OrderService orderService, PayoutBlockRepository payoutBlockRepository,
                                    AuthSessionService authSessions, AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.payoutBlockRepository = payoutBlockRepository;
        this.authSessions = authSessions;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserDTO findByEmail(String email) {
        var user = userRepository.findByEmail(email.trim())
                .filter(found -> !found.isDeleted())
                .orElseThrow(AccountSuspensionService::userNotFound);
        return details(user);
    }

    @Transactional
    public AdminUserDTO suspend(UUID userId, String reason, User admin) {
        var user = lockUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ProblemException(ProblemType.ADMIN_ACCOUNT_SUSPENSION, "Admin accounts cannot be suspended");
        }
        if (user.isSuspended()) {
            throw new ProblemException(ProblemReason.ACCOUNT_ALREADY_SUSPENDED, "Account is already suspended");
        }
        var pendingOrderIds = orderRepository.lockPendingIdsByUserId(userId);
        for (var orderId : pendingOrderIds) {
            orderService.cancelPending(orderRepository.findById(orderId).orElseThrow());
        }
        var trimmed = reason.trim();
        user.suspend(trimmed, admin.getId(), clock.instant());
        userRepository.flush();
        int revokedSessions = authSessions.revokeAll(userId, RevocationReason.ACCOUNT_SUSPENDED);

        var details = new LinkedHashMap<String, Object>();
        details.put("reason", trimmed);
        details.put("role", user.getRole().name());
        details.put("cancelledPendingOrders", pendingOrderIds.size());
        details.put("revokedSessions", revokedSessions);
        auditService.record(AuditAction.ACCOUNT_SUSPENDED, AuditTargetType.USER, userId, details);
        return details(user);
    }

    @Transactional
    public AdminUserDTO reactivate(UUID userId) {
        var user = lockUser(userId);
        var previousReason = user.getSuspensionReason();
        var suspendedAt = user.getSuspendedAt();
        user.reactivate();
        userRepository.flush();

        var details = new LinkedHashMap<String, Object>();
        details.put("previousReason", previousReason);
        details.put("suspendedAt", suspendedAt.toString());
        auditService.record(AuditAction.ACCOUNT_REACTIVATED, AuditTargetType.USER, userId, details);
        return details(user);
    }

    private User lockUser(UUID userId) {
        return userRepository.findForUpdate(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(AccountSuspensionService::userNotFound);
    }

    private AdminUserDTO details(User user) {
        return new AdminUserDTO(user, payoutBlockRepository.findById(user.getId()));
    }

    private static ProblemException userNotFound() {
        return new ProblemException(ProblemType.USER_NOT_FOUND, "User not found");
    }
}
