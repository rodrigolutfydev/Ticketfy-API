package com.lutfy.ticketfy.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByEventIdOrderByCreatedAtAscCodeAsc(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id AND c.event.id = :eventId")
    Optional<Coupon> findForUpdate(@Param("id") UUID id, @Param("eventId") UUID eventId);

    @Modifying
    @Query(value = "UPDATE coupons SET uses_count = uses_count - 1 WHERE id = :id AND uses_count > 0",
            nativeQuery = true)
    int releaseUse(@Param("id") UUID id);

    @Query("""
        SELECT o.couponId AS couponId, COUNT(o) AS paidOrders, COALESCE(SUM(o.discountAmount), 0) AS discountTotal
          FROM Order o
         WHERE o.couponId IN :ids
           AND o.status = com.lutfy.ticketfy.order.OrderStatus.PAID
         GROUP BY o.couponId
        """)
    List<CouponUsage> findPaidUsage(@Param("ids") Collection<UUID> ids);

    interface CouponUsage {
        UUID getCouponId();
        Long getPaidOrders();
        BigDecimal getDiscountTotal();
    }
}
