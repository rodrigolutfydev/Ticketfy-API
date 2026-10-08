package com.lutfy.ticketfy.privacy;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AccountDeletionQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AccountDeletionQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> lockPendingOrders(UUID userId) {
        return jdbc.queryForList("""
                SELECT id FROM orders
                 WHERE user_id = :userId AND status = 'PENDING'
                 ORDER BY id
                   FOR UPDATE
                """, new MapSqlParameterSource("userId", userId), UUID.class);
    }

    public void lockTicketTypesOfOrganizer(UUID organizerId) {
        jdbc.queryForList("""
                SELECT tt.id FROM ticket_types tt
                  JOIN events e ON e.id = tt.event_id
                 WHERE e.organizer_id = :organizerId
                 ORDER BY tt.id
                   FOR NO KEY UPDATE OF tt
                """, new MapSqlParameterSource("organizerId", organizerId), UUID.class);
    }

    public List<UUID> findEventsWithActiveSales(UUID organizerId, Instant now) {
        return jdbc.queryForList("""
                SELECT e.id FROM events e
                 WHERE e.organizer_id = :organizerId
                   AND e.cancelled_at IS NULL
                   AND ((COALESCE(e.ends_at, e.starts_at) > :now
                         AND EXISTS (SELECT 1 FROM ticket_types tt
                                      WHERE tt.event_id = e.id AND tt.quantity_sold > 0))
                        OR EXISTS (SELECT 1 FROM orders o
                                     JOIN order_items oi ON oi.order_id = o.id
                                     JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                                    WHERE tt.event_id = e.id AND o.status = 'PENDING'))
                 ORDER BY e.starts_at, e.id
                """, new MapSqlParameterSource("organizerId", organizerId).addValue("now", Timestamp.from(now)),
                UUID.class);
    }

    public int countUpcomingTickets(UUID ownerId, Instant now) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tickets t
                  JOIN ticket_types tt ON tt.id = t.ticket_type_id
                  JOIN events e ON e.id = tt.event_id
                 WHERE t.owner_id = :ownerId
                   AND t.status = 'VALID'
                   AND e.cancelled_at IS NULL
                   AND COALESCE(e.ends_at, e.starts_at) > :now
                """, new MapSqlParameterSource("ownerId", ownerId).addValue("now", Timestamp.from(now)),
                Integer.class);
        return count == null ? 0 : count;
    }
}
