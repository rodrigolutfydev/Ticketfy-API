package com.lutfy.ticketfy.payout.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_blocks")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "organizerId")
public class PayoutBlock {

    @Id
    @Column(name = "organizer_id")
    private UUID organizerId;
    private String reason;
    private UUID blockedBy;
    private Instant blockedAt;
    @Version
    private Long version;

    public PayoutBlock(UUID organizerId, String reason, UUID blockedBy, Instant blockedAt) {
        this.organizerId = organizerId;
        this.reason = reason;
        this.blockedBy = blockedBy;
        this.blockedAt = blockedAt;
    }
}
