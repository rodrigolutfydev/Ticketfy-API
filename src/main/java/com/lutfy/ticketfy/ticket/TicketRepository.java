package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.User;
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

    @EntityGraph(attributePaths = {"ticketType"})
    List<Ticket> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);

    @Query("""
        SELECT new com.lutfy.ticketfy.ticket.TicketRefs(t.order.id, t.ticketType.event.id)
          FROM Ticket t
         WHERE t.id = :id AND t.owner.id = :ownerId
        """)
    Optional<TicketRefs> findRefs(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    boolean existsByOrderIdAndTransferCountGreaterThan(UUID orderId, int transferCount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Ticket t
           SET t.owner = :recipient, t.code = :code, t.transferCount = t.transferCount + 1, t.updatedAt = :now
         WHERE t.id = :id
           AND t.owner = :owner
           AND t.status = :valid
           AND t.transferCount < :max
        """)
    int transfer(@Param("id") UUID id,
                 @Param("owner") User owner,
                 @Param("recipient") User recipient,
                 @Param("code") String code,
                 @Param("now") Instant now,
                 @Param("valid") TicketStatus valid,
                 @Param("max") int max);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Ticket t
        SET t.status = :used, t.usedAt = :now, t.updatedAt = :now
        WHERE t.code = :code AND t.status = :valid
          AND EXISTS (
              SELECT 1 FROM TicketType tt JOIN tt.event e
               WHERE tt.id = t.ticketType.id
                 AND e.cancelledAt IS NULL)
        """)
    int checkIn(@Param("code") String code,
                @Param("now") Instant now,
                @Param("used") TicketStatus used,
                @Param("valid") TicketStatus valid);

    long countByOrderId(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, TicketStatus status);

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