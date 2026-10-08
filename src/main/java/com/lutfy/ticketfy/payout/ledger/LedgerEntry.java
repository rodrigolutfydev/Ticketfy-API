package com.lutfy.ticketfy.payout.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "organizer_ledger_entries")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "organizer_id", nullable = false, updatable = false)
    private UUID organizerId;
    @Column(name = "event_id", updatable = false)
    private UUID eventId;
    @Column(name = "order_id", updatable = false)
    private UUID orderId;
    @Column(name = "payout_id", updatable = false)
    private UUID payoutId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LedgerEntryType type;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static LedgerEntry forOrder(UUID organizerId, UUID eventId, UUID orderId, LedgerEntryType type,
                                BigDecimal amount, Instant createdAt) {
        var entry = new LedgerEntry(organizerId, type, amount, createdAt);
        entry.eventId = eventId;
        entry.orderId = orderId;
        return entry;
    }

    static LedgerEntry forPayout(UUID organizerId, UUID payoutId, LedgerEntryType type,
                                 BigDecimal amount, Instant createdAt) {
        var entry = new LedgerEntry(organizerId, type, amount, createdAt);
        entry.payoutId = payoutId;
        return entry;
    }

    private LedgerEntry(UUID organizerId, LedgerEntryType type, BigDecimal amount, Instant createdAt) {
        this.organizerId = organizerId;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
    }
}
