package com.lutfy.ticketfy.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentDetailsDTO(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        PaymentMethod method,
        BigDecimal amount,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {
    public PaymentDetailsDTO(Payment payment) {
        this( payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getApprovedAt(),
                payment.getCreatedAt());
    }
}