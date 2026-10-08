package com.lutfy.ticketfy.payout.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayoutBlockRepository extends JpaRepository<PayoutBlock, UUID> {
}
