package com.lutfy.ticketfy.auth.passwordreset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findForUpdate(@Param("tokenHash") byte[] tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE PasswordResetToken t
           SET t.invalidatedAt = :now
         WHERE t.userId = :userId AND t.usedAt IS NULL AND t.invalidatedAt IS NULL
        """)
    int invalidateOpen(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
        DELETE FROM password_reset_tokens
         WHERE id IN (SELECT id FROM password_reset_tokens WHERE expires_at < :before LIMIT :limit)
        """, nativeQuery = true)
    int deleteExpiredBefore(@Param("before") Instant before, @Param("limit") int limit);
}
