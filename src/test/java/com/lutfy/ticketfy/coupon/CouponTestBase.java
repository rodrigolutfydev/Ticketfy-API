package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderDetailsDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

abstract class CouponTestBase extends IntegrationTestBase {

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentService paymentService;

    User organizer;
    UUID eventId;

    @BeforeEach
    void setUpEvent() {
        organizer = user("ORGANIZER");
        eventId = insertEvent(organizer.getId());
    }

    User user(String role) {
        return userRepository.findById(insertUser(role)).orElseThrow();
    }

    UUID lot(UUID eventId, String price, int quantity) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price,
                                          quantity_total, quantity_sold, max_per_order, active, created_at)
                VALUES (?, ?, ?, NULL, ?, ?, 0, 10, true, NOW())
                """, id, eventId, "Lote " + id, new BigDecimal(price), quantity);
        return id;
    }

    UUID coupon(UUID eventId, String code, String type, String value, Integer maxUses) {
        return coupon(eventId, code, type, value, maxUses, null, null, true);
    }

    UUID coupon(UUID eventId, String code, String type, String value, Integer maxUses,
                Instant startsAt, Instant endsAt, boolean active) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coupons (id, event_id, code, discount_type, discount_value, max_uses,
                                     starts_at, ends_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, eventId, code, type, new BigDecimal(value), maxUses,
                startsAt == null ? null : Timestamp.from(startsAt),
                endsAt == null ? null : Timestamp.from(endsAt), active);
        return id;
    }

    OrderDetailsDTO order(User buyer, UUID ticketTypeId, int quantity, String couponCode) {
        var dto = new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity)), couponCode);
        return orderService.create(dto, null, buyer);
    }

    int uses(UUID couponId) {
        return jdbc.queryForObject("SELECT uses_count FROM coupons WHERE id = ?", Integer.class, couponId);
    }

    BigDecimal orderColumn(UUID orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM orders WHERE id = ?", BigDecimal.class, orderId);
    }

    String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
