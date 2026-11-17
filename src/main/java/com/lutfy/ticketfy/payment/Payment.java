package com.lutfy.ticketfy.payment;

import com.lutfy.ticketfy.infra.exception.ProblemReason;
import com.lutfy.ticketfy.infra.exception.InvalidPaymentStateException;
import com.lutfy.ticketfy.order.Order;

import jakarta.persistence.*;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private BigDecimal amount;

    private String providerReference;

    private Instant approvedAt;

    private Instant refundedAt;

    private String refundReference;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // ==========================================
    // CONSTRUTOR
    // ==========================================

    public Payment(Order order, PaymentMethod method) {
        this.order = order;
        this.method = method;
        this.amount = order.getTotalAmount();
        this.status = PaymentStatus.PENDING;
    }

    // ==========================================
    // REFERÊNCIA DO PROVEDOR
    // ==========================================

    public void setProviderReference(String providerReference) {

        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Referência do provedor é obrigatória"
            );
        }

        if (providerReference.length() > 100) {
            throw new IllegalArgumentException(
                    "Referência do provedor excede 100 caracteres"
            );
        }

        if (this.providerReference != null
                && !this.providerReference.equals(providerReference)) {
            throw new IllegalStateException(
                    "Referência do provedor não pode ser alterada"
            );
        }

        this.providerReference = providerReference;
    }

    // ==========================================
    // APROVAR PAGAMENTO
    // ==========================================

    public void approve() {

        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    ProblemReason.PAYMENT_NOT_PENDING,
                    "Only pending payments can be approved"
            );
        }

        this.status = PaymentStatus.APPROVED;
        this.approvedAt = Instant.now();
    }

    // ==========================================
    // REEMBOLSAR PAGAMENTO
    // ==========================================

    public void refund(String reference) {

        if (status != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                    ProblemReason.PAYMENT_NOT_APPROVED,
                    "Only approved payments can be refunded"
            );
        }

        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Instant.now();
        this.refundReference = reference;
    }

    // ==========================================
    // REJEITAR PAGAMENTO
    // ==========================================

    public void reject() {

        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    ProblemReason.PAYMENT_NOT_PENDING,
                    "Only pending payments can be rejected"
            );
        }

        this.status = PaymentStatus.REJECTED;
    }
}