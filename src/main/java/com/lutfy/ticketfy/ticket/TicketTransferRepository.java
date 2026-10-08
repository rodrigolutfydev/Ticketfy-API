package com.lutfy.ticketfy.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTransferRepository extends JpaRepository<TicketTransfer, UUID> {

    List<TicketTransfer> findByTicketIdOrderByTransferredAt(UUID ticketId);
}
