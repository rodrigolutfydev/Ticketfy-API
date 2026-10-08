package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventOrderDTO(
        UUID orderId,
        OrderStatus status,
        Instant createdAt,
        Instant paidAt,
        Buyer buyer,
        List<Item> items,
        BigDecimal total,
        BigDecimal platformFee,
        BigDecimal netAmount,
        BigDecimal subtotal,
        BigDecimal discount,
        String couponCode
) {
    public record Buyer(String name, String email) {}

    public record Item(UUID ticketTypeId, String ticketTypeName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
}
