package com.lutfy.ticketfy.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}