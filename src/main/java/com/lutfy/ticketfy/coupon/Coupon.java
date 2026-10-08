package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.event.Event;
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
@Table(name = "coupons")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", updatable = false)
    private Event event;
    @Column(updatable = false)
    private String code;
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer maxUses;
    @Column(insertable = false, updatable = false)
    private Integer usesCount;
    private Instant startsAt;
    private Instant endsAt;
    private Boolean active;
    @Column(insertable = false, updatable = false)
    private Instant firstUsedAt;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public Coupon(Event event, String code, CouponSettings settings) {
        this.event = event;
        this.code = code;
        this.usesCount = 0;
        apply(settings);
    }

    public void apply(CouponSettings settings) {
        this.discountType = settings.discountType();
        this.discountValue = settings.discountValue();
        this.maxUses = settings.maxUses();
        this.startsAt = settings.startsAt();
        this.endsAt = settings.endsAt();
        this.active = settings.active();
    }

    public boolean isUsed() {
        return firstUsedAt != null;
    }

    public boolean changesDiscount(CouponSettings settings) {
        return discountType != settings.discountType() || discountValue.compareTo(settings.discountValue()) != 0;
    }
}
