package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payouts")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID organizerId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PayoutStatus status;
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;
    @Convert(converter = EncryptedStringConverter.class)
    private String document;
    private String holderName;
    @Enumerated(EnumType.STRING)
    private PixKeyType pixKeyType;
    @Convert(converter = EncryptedStringConverter.class)
    private String pixKey;
    private String transferReference;
    private String failureReason;
    private String rejectionReason;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private Instant requestedAt;
    private Instant processingStartedAt;
    private Instant finishedAt;
    @UpdateTimestamp
    private Instant updatedAt;
    @Version
    private Long version;

    public Payout(UUID organizerId, BigDecimal amount, String idempotencyKey, PayoutDestination destination,
                  PayoutStatus initialStatus, Instant requestedAt) {
        this.organizerId = organizerId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.documentType = destination.documentType();
        this.document = destination.document();
        this.holderName = destination.holderName();
        this.pixKeyType = destination.pixKeyType();
        this.pixKey = destination.pixKey();
        this.status = initialStatus;
        this.requestedAt = requestedAt;
    }

    public void cancel(Instant now) {
        if (status != PayoutStatus.REQUESTED && status != PayoutStatus.UNDER_REVIEW) {
            throw new ProblemException(ProblemType.INVALID_PAYOUT_STATE,
                    "Only requested or under review payouts can be cancelled");
        }
        this.status = PayoutStatus.CANCELLED;
        this.finishedAt = now;
    }

    public void approve(UUID reviewer, Instant now) {
        require(PayoutStatus.UNDER_REVIEW, "Only payouts under review can be approved");
        this.status = PayoutStatus.REQUESTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    public void reject(UUID reviewer, String reason, Instant now) {
        require(PayoutStatus.UNDER_REVIEW, "Only payouts under review can be rejected");
        this.status = PayoutStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.finishedAt = now;
    }

    public void startProcessing(Instant now) {
        require(PayoutStatus.REQUESTED, "Only requested payouts can be processed");
        this.status = PayoutStatus.PROCESSING;
        this.processingStartedAt = now;
    }

    public void markPaid(String reference, Instant now) {
        require(PayoutStatus.PROCESSING, "Only processing payouts can be paid");
        this.status = PayoutStatus.PAID;
        this.transferReference = reference;
        this.finishedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        require(PayoutStatus.PROCESSING, "Only processing payouts can fail");
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
        this.finishedAt = now;
    }

    public boolean inProgress() {
        return PayoutStatus.inProgress().contains(status);
    }

    public PayoutDestination destination() {
        return new PayoutDestination(documentType, document, holderName, pixKeyType, pixKey);
    }

    private void require(PayoutStatus expected, String message) {
        if (status != expected) {
            throw new ProblemException(ProblemType.INVALID_PAYOUT_STATE, message);
        }
    }
}
