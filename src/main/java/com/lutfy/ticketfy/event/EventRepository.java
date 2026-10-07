package com.lutfy.ticketfy.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndActiveTrue(UUID id);

    Page<Event> findByOrganizerIdAndActiveTrue(UUID organizerId, Pageable pageable);

    boolean existsByIdAndCancelledAtIsNotNull(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Event e
           SET e.cancelledAt = :now, e.cancellationReason = :reason, e.updatedAt = :now
         WHERE e.id = :id
           AND e.active = true
           AND e.cancelledAt IS NULL
           AND COALESCE(e.endsAt, e.startsAt) > :now
        """)
    int cancel(@Param("id") UUID id, @Param("reason") String reason, @Param("now") Instant now);
}
