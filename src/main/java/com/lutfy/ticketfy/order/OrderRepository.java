package com.lutfy.ticketfy.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant instant);

    @EntityGraph(attributePaths = {"items", "items.ticketType"})
    Optional<Order> findWithItemsById(UUID id);

    @Query("""
        SELECT DISTINCT o.id
          FROM Order o JOIN o.items i
         WHERE i.ticketType.event.id = :eventId
           AND o.status IN :statuses
        """)
    List<UUID> findIdsByEventIdAndStatusIn(@Param("eventId") UUID eventId,
                                           @Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
        SELECT DISTINCT o.id
          FROM Order o JOIN o.items i
         WHERE i.ticketType.event.cancelledAt IS NOT NULL
           AND o.status IN :statuses
           AND NOT EXISTS (
               SELECT 1 FROM Ticket t
                WHERE t.order = o
                  AND t.status = com.lutfy.ticketfy.ticket.TicketStatus.USED)
        """)
    List<UUID> findIdsAwaitingEventCancellation(@Param("statuses") Collection<OrderStatus> statuses);
}
