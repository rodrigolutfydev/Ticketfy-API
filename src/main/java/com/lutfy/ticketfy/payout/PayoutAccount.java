package com.lutfy.ticketfy.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_accounts")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "organizerId")
public class PayoutAccount {

    @Id
    @Column(name = "organizer_id")
    private UUID organizerId;
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;
    @Convert(converter = EncryptedStringConverter.class)
    private String document;
    private String holderName;
    @Enumerated(EnumType.STRING)
    private PixKeyType pixKeyType;
    @Convert(converter = EncryptedStringConverter.class)
    private String pixKey;
    private Instant keyChangedAt;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
    @Version
    private Long version;

    public PayoutAccount(UUID organizerId, PayoutDestination destination) {
        this.organizerId = organizerId;
        apply(destination);
    }

    public void update(PayoutDestination destination, Instant now) {
        if (pixKeyType != destination.pixKeyType() || !pixKey.equals(destination.pixKey())) {
            this.keyChangedAt = now;
        }
        apply(destination);
    }

    public Instant payoutsBlockedUntil(Duration cooldown) {
        return keyChangedAt == null ? null : keyChangedAt.plus(cooldown);
    }

    public PayoutDestination destination() {
        return new PayoutDestination(documentType, document, holderName, pixKeyType, pixKey);
    }

    private void apply(PayoutDestination destination) {
        this.documentType = destination.documentType();
        this.document = destination.document();
        this.holderName = destination.holderName();
        this.pixKeyType = destination.pixKeyType();
        this.pixKey = destination.pixKey();
    }
}
