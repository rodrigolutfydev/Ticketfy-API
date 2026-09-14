package com.lutfy.ticketfy.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {
    List<TicketType> findByEventIdAndActiveTrue(UUID eventId);
    Optional<TicketType> findByIdAndActiveTrue(UUID id);

    @Modifying
    @Query("""
        UPDATE TicketType t
           SET t.quantitySold = t.quantitySold + :quantity
         WHERE t.id = :id
           AND t.quantitySold + :quantity <= t.quantityTotal
        """)
    int reserveStock(@Param("id") UUID id, @Param("quantity") int quantity);
}
