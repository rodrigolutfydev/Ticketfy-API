package com.lutfy.ticketfy.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("""
            SELECT s FROM AuthSession s JOIN FETCH s.user
             WHERE s.id = :id AND s.revokedAt IS NULL AND s.expiresAt > :now
            """)
    Optional<AuthSession> findActive(UUID id, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AuthSession s SET s.revokedAt = :now, s.revokedReason = :reason
             WHERE s.user.id = :userId AND s.revokedAt IS NULL
            """)
    int revokeAll(UUID userId, RevocationReason reason, Instant now);

    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM auth_sessions
             WHERE id IN (SELECT id FROM auth_sessions
                           WHERE expires_at < :cutoff OR revoked_at < :cutoff
                           LIMIT :limit)
            """, nativeQuery = true)
    int deleteFinishedBefore(Instant cutoff, int limit);
}
