package com.lutfy.ticketfy.payout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class PayoutQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PayoutQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Balance findBalance(UUID organizerId, Instant releasedUntil) {
        var sql = """
                SELECT COALESCE(SUM(l.amount) FILTER (WHERE e.id IS NOT NULL AND e.cancelled_at IS NULL
                                    AND COALESCE(e.ends_at, e.starts_at) > :releasedUntil), 0)   AS pending,
                       COALESCE(SUM(l.amount) FILTER (WHERE l.payout_id IS NOT NULL
                                    OR (e.id IS NOT NULL AND e.cancelled_at IS NULL
                                        AND COALESCE(e.ends_at, e.starts_at) <= :releasedUntil)), 0) AS available,
                       COALESCE(SUM(l.amount) FILTER (WHERE e.cancelled_at IS NOT NULL), 0)    AS held,
                       COALESCE(SUM(l.amount), 0)                                              AS total,
                       (SELECT COALESCE(SUM(p.amount), 0) FROM payouts p
                         WHERE p.organizer_id = :organizerId
                           AND p.status IN ('REQUESTED', 'PROCESSING'))                         AS in_payout
                  FROM organizer_ledger_entries l
                  LEFT JOIN events e ON e.id = l.event_id
                 WHERE l.organizer_id = :organizerId
                """;
        var params = new MapSqlParameterSource("organizerId", organizerId)
                .addValue("releasedUntil", Timestamp.from(releasedUntil));
        return jdbc.queryForObject(sql, params, (rs, i) -> new Balance(
                rs.getBigDecimal("pending"),
                rs.getBigDecimal("available"),
                rs.getBigDecimal("held"),
                rs.getBigDecimal("total"),
                rs.getBigDecimal("in_payout")));
    }

    public Page<LedgerEntryDTO> findEntries(UUID organizerId, UUID eventId, Instant from, Instant until,
                                            Pageable pageable) {
        var params = new MapSqlParameterSource("organizerId", organizerId);
        var where = new StringBuilder(" WHERE l.organizer_id = :organizerId");
        if (eventId != null) {
            where.append(" AND l.event_id = :eventId");
            params.addValue("eventId", eventId);
        }
        if (from != null) {
            where.append(" AND l.created_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (until != null) {
            where.append(" AND l.created_at < :until");
            params.addValue("until", Timestamp.from(until));
        }

        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizer_ledger_entries l" + where, params, Long.class);

        var sql = """
                SELECT l.id, l.type, l.amount, l.event_id, e.name AS event_name, l.order_id, l.payout_id,
                       l.created_at
                  FROM organizer_ledger_entries l
                  LEFT JOIN events e ON e.id = l.event_id
                """ + where + """
                 ORDER BY l.created_at DESC, l.id DESC
                 LIMIT :limit OFFSET :offset
                """;
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var content = jdbc.query(sql, params, (rs, i) -> new LedgerEntryDTO(
                rs.getObject("id", UUID.class),
                LedgerEntryType.valueOf(rs.getString("type")),
                rs.getBigDecimal("amount"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_name"),
                rs.getObject("order_id", UUID.class),
                rs.getObject("payout_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()));
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    record Balance(BigDecimal pending, BigDecimal available, BigDecimal held, BigDecimal total,
                   BigDecimal inPayout) {}
}
