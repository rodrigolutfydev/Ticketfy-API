package com.lutfy.ticketfy.auth.passwordreset;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

// Recuperação de senha em duas etapas. O pedido sempre "dá certo" para quem
// chama; só gera token e e-mail para conta ativa. A confirmação troca a senha
// com um token de uso único e derruba todas as sessões da conta.
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessions;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final PasswordResetSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                PasswordEncoder passwordEncoder, AuthSessionService authSessions,
                                AuditService auditService, ApplicationEventPublisher events,
                                PasswordResetSettings settings, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authSessions = authSessions;
        this.auditService = auditService;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        var found = users.findByEmail(email.trim());
        if (found.isEmpty()) {
            return;
        }
        // Trava a linha do usuário: dois pedidos ao mesmo tempo não deixam dois tokens abertos.
        var user = users.findForUpdate(found.get().getId()).orElse(null);
        if (user == null || user.isDeleted() || user.isSuspended()) {
            return;
        }
        var now = clock.instant();
        int invalidated = tokens.invalidateOpen(user.getId(), now);
        var raw = newToken();
        tokens.save(new PasswordResetToken(user.getId(), hash(raw), now, now.plus(settings.tokenTtl())));
        auditService.record(AuditAction.PASSWORD_RESET_REQUESTED, AuditTargetType.USER, user.getId(),
                Map.of("invalidatedTokens", invalidated, "hasPassword", user.hasPassword()));
        events.publishEvent(new PasswordResetRequestedEvent(user.getId(), raw));
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalid();
        }
        var token = tokens.findForUpdate(hash(rawToken)).orElseThrow(PasswordResetService::invalid);
        var now = clock.instant();
        if (token.isUsed()) {
            throw new ProblemException(ProblemType.PASSWORD_RESET_TOKEN_USED, "This password reset link was already used");
        }
        if (token.isInvalidated()) {
            throw invalid();
        }
        if (token.isExpired(now)) {
            throw new ProblemException(ProblemType.PASSWORD_RESET_TOKEN_EXPIRED, "This password reset link has expired");
        }
        var user = users.findForUpdate(token.getUserId()).orElseThrow(PasswordResetService::invalid);
        if (user.isDeleted() || user.isSuspended()) {
            throw invalid();
        }
        boolean firstPassword = !user.hasPassword();
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        int revoked = authSessions.revokeAll(user.getId(), RevocationReason.PASSWORD_CHANGED);
        auditService.record(AuditAction.PASSWORD_RESET, AuditTargetType.USER, user.getId(),
                Map.of("revokedSessions", revoked, "firstPassword", firstPassword));
    }

    @Transactional
    public int deleteExpired(Duration retention, int batchSize) {
        return tokens.deleteExpiredBefore(clock.instant().minus(retention), batchSize);
    }

    private String newToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ProblemException invalid() {
        return new ProblemException(ProblemType.PASSWORD_RESET_TOKEN_INVALID, "This password reset link is not valid");
    }
}
