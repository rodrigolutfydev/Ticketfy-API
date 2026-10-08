package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EventDashboardDTO(
        UUID eventId,
        String eventName,
        Instant startsAt,
        String timeZone,
        Instant generatedAt,
        Totals totals,
        List<StatusSummary> ordersByStatus,
        CheckIn checkIn,
        List<TicketTypeSales> ticketTypes,
        List<DailySales> dailySales,
        List<CouponUsage> coupons
) {
    public record Totals(
            long ticketsSold,
            long ticketsReserved,
            long capacity,
            long remaining,
            BigDecimal percentSold,
            BigDecimal revenue,
            BigDecimal platformFee,
            BigDecimal netRevenue,
            long paidOrders,
            BigDecimal averageOrderValue,
            BigDecimal averageTicketPrice,
            BigDecimal discounts
    ) {}

    public record StatusSummary(OrderStatus status, long orders, long tickets) {}

    public record CheckIn(long checkedIn, long issued, BigDecimal attendanceRate) {}

    public record TicketTypeSales(
            UUID id,
            String name,
            BigDecimal price,
            int quantityTotal,
            long sold,
            long reserved,
            int remaining,
            BigDecimal revenue,
            BigDecimal percentSold
    ) {}

    public record DailySales(LocalDate date, long tickets, BigDecimal revenue) {}

    public record CouponUsage(
            UUID id,
            String code,
            boolean active,
            int uses,
            Integer maxUses,
            long paidOrders,
            BigDecimal discountTotal
    ) {}
}
