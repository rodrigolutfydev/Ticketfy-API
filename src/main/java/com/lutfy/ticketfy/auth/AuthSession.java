package com.lutfy.ticketfy.auth;

import com.lutfy.ticketfy.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    private RevocationReason revokedReason;

    public AuthSession(User user, Instant now, Instant expiresAt) {
        this.user = user;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public boolean revoke(RevocationReason reason, Instant now) {
        if (revokedAt != null) {
            return false;
        }
        this.revokedAt = now;
        this.revokedReason = reason;
        return true;
    }
}
