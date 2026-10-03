package com.lutfy.ticketfy.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @EntityGraph(attributePaths = {"ticketType", "ticketType.event"})
    Page<Ticket> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Ticket> findByCode(String code);

    List<Ticket> findByOrderId(UUID orderId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Ticket t
        SET t.status = :used, t.usedAt = :now, t.updatedAt = :now
        WHERE t.code = :code AND t.status = :valid
        """)
    int checkIn(@Param("code") String code,
                @Param("now") Instant now,
                @Param("used") TicketStatus used,
                @Param("valid") TicketStatus valid);

    long countByOrderId(UUID orderId);

    @Modifying(flushAutomatically = true)
    @Query("""
    UPDATE Ticket t
    SET t.status = :cancelled, t.updatedAt = :now
    WHERE t.order.id = :orderId AND t.status = :valid
    """)
    int cancelValidByOrderId(@Param("orderId") UUID orderId,
                             @Param("now") Instant now,
                             @Param("cancelled") TicketStatus cancelled,
                             @Param("valid") TicketStatus valid);
}