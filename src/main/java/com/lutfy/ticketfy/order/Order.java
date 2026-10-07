package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.user.User;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal platformFeePercent;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private Instant expiresAt;
    private String idempotencyKey;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
    @Version
    private Long version;

    public Order(User user, Instant expiresAt, String idempotencyKey) {
        this.user = user;
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>();
        this.totalAmount = BigDecimal.ZERO;
        this.platformFeePercent = BigDecimal.ZERO;
        this.platformFee = BigDecimal.ZERO;
        this.netAmount = BigDecimal.ZERO;
    }

    public void applyPlatformFee(BigDecimal percent) {
        this.platformFeePercent = percent;
        this.platformFee = totalAmount.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.netAmount = totalAmount.subtract(platformFee);
    }

    public Event event() {
        return items.get(0).getTicketType().getEvent();
    }

    public void markAsPaid() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can be paid");
        }
        this.status = OrderStatus.PAID;
    }

    public void expire() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can expire");
        }
        if (!isExpired()) {
            throw new InvalidOrderStateException("Order has not reached its expiration time");
        }
        this.status = OrderStatus.EXPIRED;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void refund() {
        if (status != OrderStatus.PAID) {
            throw new InvalidOrderStateException("Only paid orders can be refunded");
        }
        this.status = OrderStatus.REFUNDED;
    }

    public boolean belongsToCancelledEvent() {
        return items.stream().anyMatch(item -> item.getTicketType().getEvent().isCancelled());
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        this.totalAmount = this.totalAmount.add(item.subtotal());
    }

}
