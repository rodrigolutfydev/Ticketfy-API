package com.lutfy.ticketfy.ticket;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ticket_transfers")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TicketTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;
    @Column(name = "from_user_id", nullable = false, updatable = false)
    private UUID fromUserId;
    @Column(name = "to_user_id", nullable = false, updatable = false)
    private UUID toUserId;
    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt;

    public TicketTransfer(UUID ticketId, UUID fromUserId, UUID toUserId, Instant transferredAt) {
        this.ticketId = ticketId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.transferredAt = transferredAt;
    }
}
