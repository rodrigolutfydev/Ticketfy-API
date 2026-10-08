package com.lutfy.ticketfy.payout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByOrganizerIdAndIdempotencyKey(UUID organizerId, String idempotencyKey);

    Optional<Payout> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Optional<Payout> findFirstByOrganizerIdAndStatusIn(UUID organizerId, Collection<PayoutStatus> statuses);

    boolean existsByOrganizerIdAndStatusIn(UUID organizerId, Collection<PayoutStatus> statuses);

    boolean existsByOrganizerIdAndStatus(UUID organizerId, PayoutStatus status);

    Page<Payout> findByOrganizerId(UUID organizerId, Pageable pageable);

    Page<Payout> findByStatus(PayoutStatus status, Pageable pageable);

    @Query("""
        SELECT p.id FROM Payout p
         WHERE p.status = com.lutfy.ticketfy.payout.PayoutStatus.REQUESTED
           AND NOT EXISTS (SELECT b FROM PayoutBlock b WHERE b.organizerId = p.organizerId)
         ORDER BY p.requestedAt, p.id
        """)
    List<UUID> findProcessableIds();

    @Query("""
        SELECT p.id FROM Payout p
         WHERE p.status = com.lutfy.ticketfy.payout.PayoutStatus.PROCESSING
           AND p.processingStartedAt < :startedBefore
         ORDER BY p.processingStartedAt, p.id
        """)
    List<UUID> findStuckIds(@Param("startedBefore") Instant startedBefore);
}
