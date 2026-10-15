package com.lutfy.ticketfy.event;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class EventCancellationQueryRepository {

    private static final String PREVIEW = """
            WITH event_orders AS (
                SELECT o.status, o.total_amount,
                       EXISTS (SELECT 1 FROM tickets t WHERE t.order_id = o.id AND t.status = 'USED') AS has_used
                  FROM orders o
                 WHERE o.status IN ('PENDING', 'PAID')
                   AND EXISTS (SELECT 1 FROM order_items oi
                                 JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                                WHERE oi.order_id = o.id AND tt.event_id = :eventId)
            )
            SELECT COUNT(*) FILTER (WHERE status = 'PAID' AND total_amount > 0 AND NOT has_used) AS to_refund,
                   COUNT(*) FILTER (WHERE status = 'PAID' AND total_amount = 0) AS free_to_cancel,
                   COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_to_cancel,
                   COUNT(*) FILTER (WHERE status = 'PAID' AND total_amount > 0 AND has_used) AS manual
              FROM event_orders
            """;

    private static final String USED_TICKETS = """
            SELECT COUNT(*)
              FROM tickets t
              JOIN ticket_types tt ON tt.id = t.ticket_type_id
             WHERE tt.event_id = :eventId AND t.status = 'USED'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public EventCancellationQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EventCancellationPreviewDTO preview(UUID eventId) {
        var params = new MapSqlParameterSource("eventId", eventId);
        int usedTickets = countUsedTickets(eventId);
        return jdbc.queryForObject(PREVIEW, params, (rs, i) -> new EventCancellationPreviewDTO(
                eventId,
                rs.getInt("to_refund"),
                rs.getInt("free_to_cancel"),
                rs.getInt("pending_to_cancel"),
                rs.getInt("manual"),
                usedTickets));
    }

    public int countUsedTickets(UUID eventId) {
        var count = jdbc.queryForObject(USED_TICKETS, new MapSqlParameterSource("eventId", eventId), Long.class);
        return count == null ? 0 : count.intValue();
    }
}
