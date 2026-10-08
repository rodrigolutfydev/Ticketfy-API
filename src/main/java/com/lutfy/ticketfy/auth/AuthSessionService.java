package com.lutfy.ticketfy.auth;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthSessionService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final Pattern REFRESH_TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final AuthSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(AuthSessionRepository sessions, RefreshTokenRepository refreshTokens,
                              TokenService tokenService, AuditService auditService,
                              AuthSettings settings, Clock clock) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession start(User user) {
        var now = clock.instant();
        var session = sessions.save(new AuthSession(user, now, now.plus(settings.sessionMaxAge())));
        var refreshToken = newRefreshToken(session, now);
        return issue(session, refreshToken, now);
    }

    @Transactional
    public Optional<IssuedSession> refresh(String rawToken) {
        if (!isWellFormed(rawToken)) {
            return Optional.empty();
        }
        var now = clock.instant();
        var current = refreshTokens.findForUpdate(hash(rawToken)).orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        var session = current.getSession();
        if (current.isUsed()) {
            if (session.revoke(RevocationReason.REUSE_DETECTED, now)) {
                auditService.record(AuditAction.SESSION_REUSE_DETECTED, AuditTargetType.USER,
                        session.getUser().getId(), Map.of("sessionId", session.getId()));
            }
            return Optional.empty();
        }
        if (!session.isActive(now) || current.isExpired(now)) {
            return Optional.empty();
        }
        session.touch(now);
        var successor = newRefreshToken(session, now);
        current.replaceWith(successor.entity(), now);
        return Optional.of(issue(session, successor, now));
    }

    @Transactional
    public void logout(String rawToken) {
        if (!isWellFormed(rawToken)) {
            return;
        }
        var now = clock.instant();
        refreshTokens.findForUpdate(hash(rawToken))
                .ifPresent(token -> token.getSession().revoke(RevocationReason.LOGOUT, now));
    }

    @Transactional
    public int revokeAll(UUID userId, RevocationReason reason) {
        return sessions.revokeAll(userId, reason, clock.instant());
    }

    public Optional<User> authenticate(String accessToken) {
        return tokenService.validateToken(accessToken)
                .flatMap(claims -> sessions.findActive(claims.sessionId(), clock.instant())
                        .map(AuthSession::getUser)
                        .filter(user -> user.getId().equals(claims.userId())));
    }

    public int deleteFinished(Duration retention, int batchSize) {
        return sessions.deleteFinishedBefore(clock.instant().minus(retention), batchSize);
    }

    private IssuedSession issue(AuthSession session, NewRefreshToken refreshToken, Instant now) {
        var accessToken = tokenService.generateToken(session.getUser().getId(), session.getId(), now);
        return new IssuedSession(accessToken, settings.accessTokenTtl(), refreshToken.raw(),
                Duration.between(now, refreshToken.entity().getExpiresAt()));
    }

    private NewRefreshToken newRefreshToken(AuthSession session, Instant now) {
        var bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var idleExpiry = now.plus(settings.refreshTokenIdleTtl());
        var expiresAt = idleExpiry.isBefore(session.getExpiresAt()) ? idleExpiry : session.getExpiresAt();
        var entity = refreshTokens.save(new RefreshToken(session, hash(raw), now, expiresAt));
        return new NewRefreshToken(entity, raw);
    }

    private static boolean isWellFormed(String rawToken) {
        return rawToken != null && REFRESH_TOKEN_FORMAT.matcher(rawToken).matches();
    }

    static byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record NewRefreshToken(RefreshToken entity, String raw) {
    }
}
