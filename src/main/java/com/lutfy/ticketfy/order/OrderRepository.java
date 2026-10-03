package com.lutfy.ticketfy.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant instant);

    @EntityGraph(attributePaths = {"items", "items.ticketType"})
    Optional<Order> findWithItemsById(UUID id);
}
