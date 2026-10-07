package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Aggregation queries for the organizer dashboard. Everything is summed in the database,
 * always per order item, because one order can contain items from different events.
 */
@Repository
public class DashboardQueryRepository {

    // Sales of each ticket type: paid and pending quantities come from the frozen order items
    private static final String LOT_SALES = """
            SELECT tt.id, tt.event_id, tt.name, tt.price, tt.quantity_total, tt.quantity_sold,
                   COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)                  AS sold,
                   COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'PENDING'), 0)               AS reserved,
                   COALESCE(SUM(oi.unit_price * oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)  AS revenue
              FROM ticket_types tt
              LEFT JOIN order_items oi ON oi.ticket_type_id = tt.id
              LEFT JOIN orders o ON o.id = oi.order_id
             WHERE %s
             GROUP BY tt.id
            """;

    private static final String EVENT_ORDERS_FROM = """
              FROM orders o
              JOIN users u ON u.id = o.user_id
              JOIN order_items oi ON oi.order_id = o.id
              JOIN ticket_types tt ON tt.id = oi.ticket_type_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LotSales> findLotSales(UUID eventId) {
        var sql = LOT_SALES.formatted("tt.event_id = :eventId") + " ORDER BY tt.created_at, tt.name";
        return jdbc.query(sql, new MapSqlParameterSource("eventId", eventId), (rs, i) -> new LotSales(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity_total"),
                rs.getInt("quantity_sold"),
                rs.getLong("sold"),
                rs.getLong("reserved"),
                rs.getBigDecimal("revenue")));
    }

    public List<StatusCount> countByStatus(UUID eventId) {
        var sql = """
                SELECT o.status, COUNT(DISTINCT o.id) AS orders, SUM(oi.quantity) AS tickets
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                 WHERE tt.event_id = :eventId
                 GROUP BY o.status
                """;
        return jdbc.query(sql, new MapSqlParameterSource("eventId", eventId), (rs, i) -> new StatusCount(
                OrderStatus.valueOf(rs.getString("status")),
                rs.getLong("orders"),
                rs.getLong("tickets")));
    }

    public CheckInCount countCheckIns(UUID eventId) {
        var sql = """
                SELECT COUNT(*) FILTER (WHERE t.status = 'USED')               AS checked_in,
                       COUNT(*) FILTER (WHERE t.status IN ('VALID', 'USED'))   AS issued
                  FROM tickets t
                  JOIN ticket_types tt ON tt.id = t.ticket_type_id
                 WHERE tt.event_id = :eventId
                """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("eventId", eventId), (rs, i) ->
                new CheckInCount(rs.getLong("checked_in"), rs.getLong("issued")));
    }

    public List<DailySales> findDailySales(UUID eventId, ZoneId zone) {
        var sql = """
                SELECT CAST(p.approved_at AT TIME ZONE :zone AS DATE) AS day,
                       SUM(oi.quantity)                               AS tickets,
                       SUM(oi.unit_price * oi.quantity)               AS revenue
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                  JOIN payments p ON p.order_id = o.id AND p.status = 'APPROVED'
                 WHERE tt.event_id = :eventId
                   AND o.status = 'PAID'
                 GROUP BY day
                 ORDER BY day
                """;
        var params = new MapSqlParameterSource("eventId", eventId).addValue("zone", zone.getId());
        return jdbc.query(sql, params, (rs, i) -> new DailySales(
                rs.getObject("day", LocalDate.class),
                rs.getLong("tickets"),
                rs.getBigDecimal("revenue")));
    }

    public Page<EventOrder> findEventOrders(UUID eventId, OrderStatus status, String search, Pageable pageable) {
        var params = new MapSqlParameterSource("eventId", eventId);
        var where = new StringBuilder(" WHERE tt.event_id = :eventId");
        if (status != null) {
            where.append(" AND o.status = :status");
            params.addValue("status", status.name());
        }
        if (search != null) {
            where.append(" AND (u.name ILIKE :pattern ESCAPE '\\' OR u.email ILIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", "%" + escapeLike(search) + "%");
        }

        var total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT o.id)" + EVENT_ORDERS_FROM + where, params, Long.class);

        var sql = """
                SELECT o.id, o.status, o.created_at, u.name, u.email,
                       SUM(oi.unit_price * oi.quantity) AS total,
                       (SELECT p.approved_at FROM payments p
                         WHERE p.order_id = o.id AND p.status = 'APPROVED') AS paid_at
                """ + EVENT_ORDERS_FROM + where + """
                 GROUP BY o.id, u.name, u.email
                 ORDER BY o.created_at DESC, o.id
                 LIMIT :limit OFFSET :offset
                """;
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var content = jdbc.query(sql, params, (rs, i) -> new EventOrder(
                rs.getObject("id", UUID.class),
                OrderStatus.valueOf(rs.getString("status")),
                toInstant(rs, "created_at"),
                toInstant(rs, "paid_at"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getBigDecimal("total")));
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<EventOrderItem> findEventOrderItems(UUID eventId, Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        var sql = """
                SELECT oi.order_id, tt.id AS ticket_type_id, tt.name, oi.quantity, oi.unit_price
                  FROM order_items oi
                  JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                 WHERE oi.order_id IN (:orderIds)
                   AND tt.event_id = :eventId
                 ORDER BY oi.created_at, tt.name
                """;
        var params = new MapSqlParameterSource("eventId", eventId).addValue("orderIds", orderIds);
        return jdbc.query(sql, params, (rs, i) -> new EventOrderItem(
                rs.getObject("order_id", UUID.class),
                rs.getObject("ticket_type_id", UUID.class),
                rs.getString("name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price")));
    }

    public List<EventSales> findSalesByEvent(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        var sql = "WITH lots AS (" + LOT_SALES.formatted("tt.event_id IN (:eventIds)") + """
                )
                SELECT event_id, SUM(quantity_total) AS capacity, SUM(sold) AS sold, SUM(revenue) AS revenue
                  FROM lots
                 GROUP BY event_id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("eventIds", eventIds), (rs, i) -> new EventSales(
                rs.getObject("event_id", UUID.class),
                rs.getLong("capacity"),
                rs.getLong("sold"),
                rs.getBigDecimal("revenue")));
    }

    public OrganizerSales findOrganizerSales(UUID organizerId) {
        var sql = "WITH lots AS (" + LOT_SALES.formatted(
                "tt.event_id IN (SELECT e.id FROM events e WHERE e.organizer_id = :organizerId AND e.active)") + """
                )
                SELECT COALESCE(SUM(sold), 0) AS sold, COALESCE(SUM(revenue), 0) AS revenue
                  FROM lots
                """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("organizerId", organizerId), (rs, i) ->
                new OrganizerSales(rs.getLong("sold"), rs.getBigDecimal("revenue")));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    record LotSales(UUID id, String name, BigDecimal price, int quantityTotal, int quantitySold,
                    long sold, long reserved, BigDecimal revenue) {}

    record StatusCount(OrderStatus status, long orders, long tickets) {}

    record CheckInCount(long checkedIn, long issued) {}

    record DailySales(LocalDate date, long tickets, BigDecimal revenue) {}

    record EventOrder(UUID id, OrderStatus status, Instant createdAt, Instant paidAt,
                      String buyerName, String buyerEmail, BigDecimal total) {}

    record EventOrderItem(UUID orderId, UUID ticketTypeId, String ticketTypeName, int quantity, BigDecimal unitPrice) {}

    record EventSales(UUID eventId, long capacity, long sold, BigDecimal revenue) {}

    record OrganizerSales(long sold, BigDecimal revenue) {}
}
