package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.privacy.DataExportDTO.CouponEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.EventEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.EventRef;
import com.lutfy.ticketfy.privacy.DataExportDTO.OrderEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.OrderItemEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.PaymentEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.SalesSummary;
import com.lutfy.ticketfy.privacy.DataExportDTO.TicketEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.TicketTypeEntry;
import com.lutfy.ticketfy.privacy.DataExportDTO.TransferEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Repository
public class DataExportQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DataExportQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Instant> findRecentExports(UUID userId, Instant since) {
        return jdbc.query("""
                SELECT created_at FROM audit_log
                 WHERE target_type = 'USER' AND target_id = :userId AND action = 'DATA_EXPORTED'
                   AND created_at > :since
                 ORDER BY created_at
                """, new MapSqlParameterSource("userId", userId).addValue("since", Timestamp.from(since)),
                (rs, i) -> instant(rs, "created_at"));
    }

    public List<OrderEntry> findOrders(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        var items = new HashMap<UUID, List<OrderItemEntry>>();
        var events = new HashMap<UUID, EventRef>();
        jdbc.query("""
                SELECT oi.order_id, tt.name AS ticket_type_name, oi.unit_price, oi.quantity,
                       e.id AS event_id, e.name AS event_name, e.starts_at
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                  JOIN events e ON e.id = tt.event_id
                 WHERE o.user_id = :userId
                 ORDER BY oi.created_at, oi.id
                """, params, rs -> {
            var orderId = rs.getObject("order_id", UUID.class);
            var unitPrice = rs.getBigDecimal("unit_price");
            var quantity = rs.getInt("quantity");
            items.computeIfAbsent(orderId, id -> new ArrayList<>()).add(new OrderItemEntry(
                    rs.getString("ticket_type_name"), unitPrice, quantity,
                    unitPrice.multiply(BigDecimal.valueOf(quantity))));
            events.putIfAbsent(orderId, eventRef(rs));
        });
        var payments = new HashMap<UUID, List<PaymentEntry>>();
        jdbc.query("""
                SELECT p.order_id, p.method, p.status, p.amount, p.created_at, p.approved_at
                  FROM payments p
                  JOIN orders o ON o.id = p.order_id
                 WHERE o.user_id = :userId
                 ORDER BY p.created_at, p.id
                """, params, rs -> {
            payments.computeIfAbsent(rs.getObject("order_id", UUID.class), id -> new ArrayList<>())
                    .add(new PaymentEntry(rs.getString("method"), rs.getString("status"), rs.getBigDecimal("amount"),
                            instant(rs, "created_at"), instant(rs, "approved_at")));
        });
        return jdbc.query("""
                SELECT id, status, subtotal_amount, discount_amount, total_amount, coupon_code, created_at, expires_at
                  FROM orders
                 WHERE user_id = :userId
                 ORDER BY created_at, id
                """, params, (rs, i) -> {
            var id = rs.getObject("id", UUID.class);
            return new OrderEntry(id, rs.getString("status"), rs.getBigDecimal("subtotal_amount"),
                    rs.getBigDecimal("discount_amount"), rs.getBigDecimal("total_amount"), rs.getString("coupon_code"),
                    instant(rs, "created_at"), instant(rs, "expires_at"), events.get(id),
                    items.getOrDefault(id, List.of()), payments.getOrDefault(id, List.of()));
        });
    }

    public List<TicketEntry> findOwnedTickets(UUID userId) {
        return jdbc.query("""
                SELECT t.id, t.code, t.status, t.transfer_count, t.used_at, t.created_at,
                       tt.name AS ticket_type_name, e.id AS event_id, e.name AS event_name, e.starts_at
                  FROM tickets t
                  JOIN ticket_types tt ON tt.id = t.ticket_type_id
                  JOIN events e ON e.id = tt.event_id
                 WHERE t.owner_id = :userId
                 ORDER BY t.created_at, t.id
                """, new MapSqlParameterSource("userId", userId), (rs, i) -> new TicketEntry(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("status"), eventRef(rs),
                rs.getString("ticket_type_name"), rs.getInt("transfer_count"), instant(rs, "used_at"),
                instant(rs, "created_at")));
    }

    public List<TransferEntry> findTransfers(UUID userId) {
        return jdbc.query("""
                SELECT tr.ticket_id, tr.transferred_at,
                       CASE WHEN tr.from_user_id = :userId THEN 'SENT' ELSE 'RECEIVED' END AS direction,
                       tt.name AS ticket_type_name, e.id AS event_id, e.name AS event_name, e.starts_at
                  FROM ticket_transfers tr
                  JOIN tickets t ON t.id = tr.ticket_id
                  JOIN ticket_types tt ON tt.id = t.ticket_type_id
                  JOIN events e ON e.id = tt.event_id
                 WHERE tr.from_user_id = :userId OR tr.to_user_id = :userId
                 ORDER BY tr.transferred_at, tr.id
                """, new MapSqlParameterSource("userId", userId), (rs, i) -> new TransferEntry(
                rs.getObject("ticket_id", UUID.class), rs.getString("direction"), eventRef(rs),
                rs.getString("ticket_type_name"), instant(rs, "transferred_at")));
    }

    public boolean hasEvents(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM events WHERE organizer_id = :userId)",
                new MapSqlParameterSource("userId", userId), Boolean.class));
    }

    public List<EventEntry> findEvents(UUID organizerId) {
        var params = new MapSqlParameterSource("organizerId", organizerId);
        var ticketTypes = new HashMap<UUID, List<TicketTypeEntry>>();
        jdbc.query("""
                SELECT tt.event_id, tt.id, tt.name, tt.description, tt.price, tt.quantity_total, tt.quantity_sold,
                       tt.max_per_order, tt.active
                  FROM ticket_types tt
                  JOIN events e ON e.id = tt.event_id
                 WHERE e.organizer_id = :organizerId
                 ORDER BY tt.created_at, tt.id
                """, params, rs -> {
            ticketTypes.computeIfAbsent(rs.getObject("event_id", UUID.class), id -> new ArrayList<>())
                    .add(new TicketTypeEntry(rs.getObject("id", UUID.class), rs.getString("name"),
                            rs.getString("description"), rs.getBigDecimal("price"), rs.getInt("quantity_total"),
                            rs.getInt("quantity_sold"), rs.getObject("max_per_order", Integer.class),
                            rs.getBoolean("active")));
        });
        var coupons = new HashMap<UUID, List<CouponEntry>>();
        jdbc.query("""
                SELECT c.event_id, c.code, c.discount_type, c.discount_value, c.max_uses, c.uses_count,
                       c.starts_at, c.ends_at, c.active
                  FROM coupons c
                  JOIN events e ON e.id = c.event_id
                 WHERE e.organizer_id = :organizerId
                 ORDER BY c.created_at, c.id
                """, params, rs -> {
            coupons.computeIfAbsent(rs.getObject("event_id", UUID.class), id -> new ArrayList<>())
                    .add(new CouponEntry(rs.getString("code"), rs.getString("discount_type"),
                            rs.getBigDecimal("discount_value"), rs.getObject("max_uses", Integer.class),
                            rs.getInt("uses_count"), instant(rs, "starts_at"), instant(rs, "ends_at"),
                            rs.getBoolean("active")));
        });
        var sales = new LinkedHashMap<UUID, SalesSummary>();
        jdbc.query("""
                SELECT ev.event_id, COUNT(*) AS paid_orders, COALESCE(SUM(o.total_amount), 0) AS paid_amount
                  FROM orders o
                  JOIN LATERAL (SELECT tt.event_id
                                  FROM order_items oi
                                  JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                                 WHERE oi.order_id = o.id
                                 LIMIT 1) ev ON TRUE
                  JOIN events e ON e.id = ev.event_id
                 WHERE e.organizer_id = :organizerId AND o.status = 'PAID'
                 GROUP BY ev.event_id
                """, params, rs -> {
            sales.put(rs.getObject("event_id", UUID.class),
                    new SalesSummary(rs.getLong("paid_orders"), rs.getBigDecimal("paid_amount")));
        });
        return jdbc.query("""
                SELECT id, name, description, image_url, venue_name, address, city, state, starts_at, ends_at,
                       active, featured, cancelled_at, cancellation_reason, created_at
                  FROM events
                 WHERE organizer_id = :organizerId
                 ORDER BY created_at, id
                """, params, (rs, i) -> {
            var id = rs.getObject("id", UUID.class);
            return new EventEntry(id, rs.getString("name"), rs.getString("description"), rs.getString("image_url"),
                    rs.getString("venue_name"), rs.getString("address"), rs.getString("city"), rs.getString("state"),
                    instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getBoolean("active"),
                    rs.getBoolean("featured"), instant(rs, "cancelled_at"), rs.getString("cancellation_reason"),
                    instant(rs, "created_at"), ticketTypes.getOrDefault(id, List.of()),
                    coupons.getOrDefault(id, List.of()),
                    sales.getOrDefault(id, new SalesSummary(0, BigDecimal.ZERO)));
        });
    }

    private static EventRef eventRef(ResultSet rs) throws SQLException {
        return new EventRef(rs.getObject("event_id", UUID.class), rs.getString("event_name"),
                instant(rs, "starts_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
