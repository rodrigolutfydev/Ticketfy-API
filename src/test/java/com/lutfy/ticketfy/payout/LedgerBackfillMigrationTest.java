package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerBackfillMigrationTest extends IntegrationTestBase {

    private static final Instant CREATED = Instant.parse("2026-01-10T12:00:00Z");

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate legacy;
    private UUID organizerId;
    private UUID buyerId;

    @BeforeEach
    void createDatabaseAtV14() {
        var database = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        dataSource = new DriverManagerDataSource(url.replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1"),
                username, password);
        flyway("14").migrate();
        legacy = new JdbcTemplate(dataSource);
        organizerId = insertLegacyUser("ORGANIZER");
        buyerId = insertLegacyUser("USER");
    }

    @Test
    void backfillsZeroFeeCreditsAndDebitsForHistoricalOrders() {
        var eventId = insertLegacyEvent();
        var ticketTypeId = insertLegacyTicketType(eventId);

        var paid = insertLegacyOrder("PAID", "100.00", null);
        insertLegacyItem(paid, ticketTypeId, "100.00");
        insertLegacyPayment(paid, "APPROVED", "100.00", "2026-01-10T12:05:00Z", null);

        var refunded = insertLegacyOrder("REFUNDED", "50.00", "2026-01-12T09:00:00Z");
        insertLegacyItem(refunded, ticketTypeId, "50.00");
        insertLegacyPayment(refunded, "REFUNDED", "50.00", "2026-01-10T12:10:00Z", "2026-01-11T08:00:00Z");

        var refundedWithoutRefundDate = insertLegacyOrder("REFUNDED", "30.00", "2026-01-13T10:00:00Z");
        insertLegacyItem(refundedWithoutRefundDate, ticketTypeId, "30.00");
        insertLegacyPayment(refundedWithoutRefundDate, "APPROVED", "30.00", "2026-01-10T12:15:00Z", null);

        var refundedWithoutPayment = insertLegacyOrder("REFUNDED", "20.00", null);
        insertLegacyItem(refundedWithoutPayment, ticketTypeId, "20.00");

        var pending = insertLegacyOrder("PENDING", "70.00", null);
        insertLegacyItem(pending, ticketTypeId, "70.00");

        var free = insertLegacyOrder("PAID", "0.00", null);
        insertLegacyItem(free, ticketTypeId, "0.00");
        insertLegacyPayment(free, "APPROVED", "0.00", "2026-01-10T12:20:00Z", null);

        flyway(null).migrate();

        for (var orderId : List.of(paid, refunded, refundedWithoutRefundDate, refundedWithoutPayment, pending, free)) {
            var order = legacy.queryForMap(
                    "SELECT total_amount, platform_fee_percent, platform_fee, net_amount FROM orders WHERE id = ?", orderId);
            assertThat((BigDecimal) order.get("platform_fee_percent")).isEqualByComparingTo("0");
            assertThat((BigDecimal) order.get("platform_fee")).isEqualByComparingTo("0");
            assertThat(order.get("net_amount")).isEqualTo(order.get("total_amount"));
        }

        assertThat(entries(paid)).containsExactly(
                entry("SALE_CREDIT", "100.00", "2026-01-10T12:05:00Z"));
        assertThat(entries(refunded)).containsExactly(
                entry("SALE_CREDIT", "50.00", "2026-01-10T12:10:00Z"),
                entry("REFUND_DEBIT", "-50.00", "2026-01-11T08:00:00Z"));
        assertThat(entries(refundedWithoutRefundDate)).containsExactly(
                entry("SALE_CREDIT", "30.00", "2026-01-10T12:15:00Z"),
                entry("REFUND_DEBIT", "-30.00", "2026-01-13T10:00:00Z"));
        assertThat(entries(refundedWithoutPayment)).containsExactly(
                entry("SALE_CREDIT", "20.00", CREATED.toString()),
                entry("REFUND_DEBIT", "-20.00", CREATED.toString()));
        assertThat(entries(pending)).isEmpty();
        assertThat(entries(free)).isEmpty();
        assertThat(legacy.queryForList(
                "SELECT DISTINCT organizer_id FROM organizer_ledger_entries", UUID.class))
                .containsExactly(organizerId);
        assertThat(legacy.queryForList(
                "SELECT DISTINCT event_id FROM organizer_ledger_entries", UUID.class))
                .containsExactly(eventId);
    }

    @Test
    void abortsWhenPaidOrderSpansTwoEvents() {
        var firstTicketType = insertLegacyTicketType(insertLegacyEvent());
        var secondTicketType = insertLegacyTicketType(insertLegacyEvent());
        var mixed = insertLegacyOrder("PAID", "30.00", null);
        insertLegacyItem(mixed, firstTicketType, "10.00");
        insertLegacyItem(mixed, secondTicketType, "20.00");
        insertLegacyPayment(mixed, "APPROVED", "30.00", "2026-01-10T12:05:00Z", null);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Paid orders spanning more than one event must be fixed before V15");
        assertThat(legacy.queryForObject(
                "SELECT to_regclass('organizer_ledger_entries') IS NULL", Boolean.class)).isTrue();
        assertThat(legacy.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_name = 'orders' AND column_name = 'net_amount'
                """, Integer.class)).isZero();
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private List<Map.Entry<String, String>> entries(UUID orderId) {
        return legacy.query("""
                SELECT type, amount, created_at FROM organizer_ledger_entries
                 WHERE order_id = ?
                 ORDER BY CASE type WHEN 'SALE_CREDIT' THEN 0 ELSE 1 END
                """, (rs, i) -> Map.entry(rs.getString("type"),
                rs.getBigDecimal("amount").toPlainString() + "@"
                        + rs.getObject("created_at", OffsetDateTime.class).toInstant()), orderId);
    }

    private static Map.Entry<String, String> entry(String type, String amount, String createdAt) {
        return Map.entry(type, amount + "@" + Instant.parse(createdAt));
    }

    private UUID insertLegacyUser(String role) {
        var id = UUID.randomUUID();
        legacy.update("INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
                id, "Legacy " + role, id + "@legacy.com", "not-used", role);
        return id;
    }

    private UUID insertLegacyEvent() {
        var id = UUID.randomUUID();
        legacy.update("""
                INSERT INTO events (id, name, description, venue_name, address, city, state,
                                    starts_at, ends_at, organizer_id, active, created_at)
                VALUES (?, 'Legacy event', 'Before V15', 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ',
                        ?, NULL, ?, true, NOW())
                """, id, Timestamp.from(Instant.parse("2026-02-01T20:00:00Z")), organizerId);
        return id;
    }

    private UUID insertLegacyTicketType(UUID eventId) {
        var id = UUID.randomUUID();
        legacy.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price,
                                          quantity_total, quantity_sold, max_per_order, active, created_at)
                VALUES (?, ?, ?, 'Legacy lot', 100.00, 100, 0, 10, true, NOW())
                """, id, eventId, "Lot " + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertLegacyOrder(String status, String total, String updatedAt) {
        var id = UUID.randomUUID();
        legacy.update("""
                INSERT INTO orders (id, user_id, status, total_amount, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, buyerId, status, new BigDecimal(total), Timestamp.from(CREATED.plusSeconds(900)),
                Timestamp.from(CREATED), updatedAt == null ? null : Timestamp.from(Instant.parse(updatedAt)));
        return id;
    }

    private void insertLegacyItem(UUID orderId, UUID ticketTypeId, String unitPrice) {
        legacy.update("INSERT INTO order_items (id, order_id, ticket_type_id, unit_price, quantity) VALUES (?, ?, ?, ?, 1)",
                UUID.randomUUID(), orderId, ticketTypeId, new BigDecimal(unitPrice));
    }

    private void insertLegacyPayment(UUID orderId, String status, String amount, String approvedAt, String refundedAt) {
        legacy.update("""
                INSERT INTO payments (id, order_id, status, method, amount, approved_at, refunded_at)
                VALUES (?, ?, ?, 'SIMULATED', ?, ?, ?)
                """, UUID.randomUUID(), orderId, status, new BigDecimal(amount),
                Timestamp.from(Instant.parse(approvedAt)),
                refundedAt == null ? null : Timestamp.from(Instant.parse(refundedAt)));
    }
}
