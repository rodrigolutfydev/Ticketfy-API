package com.lutfy.ticketfy.auth;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionCleanupIntegrationTest extends IntegrationTestBase {

    @Test
    void deletesSessionsFinishedBeforeTheRetentionWindow() {
        var userId = insertUser("USER");
        var active = insertSession(userId, "NOW() - INTERVAL '1 hour'", "NOW() + INTERVAL '11 hours'", null);
        var expiredLongAgo = insertSession(userId, "NOW() - INTERVAL '9 days'", "NOW() - INTERVAL '8 days'", null);
        var expiredRecently = insertSession(userId, "NOW() - INTERVAL '2 days'", "NOW() - INTERVAL '1 day'", null);
        var revokedLongAgo = insertSession(userId, "NOW() - INTERVAL '8 days'", "NOW() + INTERVAL '1 hour'",
                "NOW() - INTERVAL '8 days'");
        var revokedRecently = insertSession(userId, "NOW() - INTERVAL '1 hour'", "NOW() + INTERVAL '11 hours'",
                "NOW() - INTERVAL '30 minutes'");
        insertRefreshToken(expiredLongAgo);
        insertRefreshToken(active);

        int deleted = new AuthSessionCleanupJob(authSessions, 7).deleteAll();

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM auth_sessions WHERE user_id = ?", UUID.class, userId))
                .containsExactlyInAnyOrder(active, expiredRecently, revokedRecently)
                .doesNotContain(expiredLongAgo, revokedLongAgo);
        assertThat(jdbc.queryForList("SELECT session_id FROM refresh_tokens WHERE session_id IN (?, ?)", UUID.class,
                active, expiredLongAgo)).isEqualTo(List.of(active));
    }

    private UUID insertSession(UUID userId, String createdAt, String expiresAt, String revokedAt) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_sessions (id, user_id, created_at, last_used_at, expires_at, revoked_at, revoked_reason) "
                        + "VALUES (?, ?, " + createdAt + ", " + createdAt + ", " + expiresAt + ", "
                        + (revokedAt == null ? "NULL, NULL" : revokedAt + ", 'LOGOUT'") + ")",
                id, userId);
        return id;
    }

    private void insertRefreshToken(UUID sessionId) {
        jdbc.update("""
                INSERT INTO refresh_tokens (id, session_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, NOW() - INTERVAL '1 minute', NOW() + INTERVAL '29 minutes')
                """, UUID.randomUUID(), sessionId, (Object) AuthSessionService.hash(UUID.randomUUID().toString()));
    }
}
