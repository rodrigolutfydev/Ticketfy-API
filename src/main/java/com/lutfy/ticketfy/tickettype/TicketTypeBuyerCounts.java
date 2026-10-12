package com.lutfy.ticketfy.tickettype;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class TicketTypeBuyerCounts {

    private static final String UPSERT = """
            INSERT INTO ticket_type_buyer_counts (ticket_type_id, user_id, quantity)
            VALUES (:ticketTypeId, :userId, :quantity)
            ON CONFLICT (ticket_type_id, user_id) DO UPDATE
               SET quantity = ticket_type_buyer_counts.quantity + EXCLUDED.quantity
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TicketTypeBuyerCounts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean consume(UUID ticketTypeId, UUID userId, int quantity, Integer limit) {
        var params = params(ticketTypeId, userId).addValue("quantity", quantity);
        if (limit == null) {
            jdbc.update(UPSERT, params);
            return true;
        }
        if (quantity > limit) {
            return false;
        }
        var sql = UPSERT + """
                 WHERE ticket_type_buyer_counts.quantity + EXCLUDED.quantity <= :limit
                RETURNING quantity
                """;
        return !jdbc.queryForList(sql, params.addValue("limit", limit), Integer.class).isEmpty();
    }

    public void release(UUID ticketTypeId, UUID userId, int quantity) {
        jdbc.update("""
                UPDATE ticket_type_buyer_counts
                   SET quantity = quantity - :quantity
                 WHERE ticket_type_id = :ticketTypeId AND user_id = :userId AND quantity >= :quantity
                """, params(ticketTypeId, userId).addValue("quantity", quantity));
    }

    public int count(UUID ticketTypeId, UUID userId) {
        return jdbc.queryForList("""
                SELECT quantity FROM ticket_type_buyer_counts
                 WHERE ticket_type_id = :ticketTypeId AND user_id = :userId
                """, params(ticketTypeId, userId), Integer.class).stream().findFirst().orElse(0);
    }

    private static MapSqlParameterSource params(UUID ticketTypeId, UUID userId) {
        return new MapSqlParameterSource("ticketTypeId", ticketTypeId).addValue("userId", userId);
    }
}
