package com.lutfy.ticketfy.auth.passwordreset;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private byte[] tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant invalidatedAt;

    public PasswordResetToken(UUID userId, byte[] tokenHash, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isInvalidated() {
        return invalidatedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
