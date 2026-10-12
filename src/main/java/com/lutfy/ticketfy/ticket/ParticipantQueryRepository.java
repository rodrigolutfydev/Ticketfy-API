package com.lutfy.ticketfy.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ParticipantQueryRepository {

    private static final String FROM = """
              FROM tickets t
              JOIN ticket_types tt ON tt.id = t.ticket_type_id
              JOIN users u ON u.id = t.owner_id
             WHERE tt.event_id = :eventId
               AND (CAST(:ticketTypeId AS UUID) IS NULL OR t.ticket_type_id = CAST(:ticketTypeId AS UUID))
               AND (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
               AND (CAST(:name AS VARCHAR) IS NULL OR u.name ILIKE CAST(:name AS VARCHAR) ESCAPE '\\')
            """;

    private static final String SELECT = """
            SELECT t.id, t.ticket_type_id, tt.name AS ticket_type_name, t.status, t.used_at,
                   t.transfer_count > 0 AS transferred, u.name AS holder_name, RIGHT(t.code, 4) AS code_suffix
            """ + FROM + """
             ORDER BY LOWER(u.name), t.id
            """;

    private static final RowMapper<ParticipantDTO> MAPPER = (rs, i) -> new ParticipantDTO(
            rs.getObject("id", UUID.class),
            rs.getObject("ticket_type_id", UUID.class),
            rs.getString("ticket_type_name"),
            TicketStatus.valueOf(rs.getString("status")),
            rs.getObject("used_at", OffsetDateTime.class) == null ? null
                    : rs.getObject("used_at", OffsetDateTime.class).toInstant(),
            rs.getBoolean("transferred"),
            rs.getString("holder_name"),
            rs.getString("code_suffix"));

    private final NamedParameterJdbcTemplate jdbc;

    public ParticipantQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<ParticipantDTO> find(ParticipantFilter filter, Pageable pageable) {
        var params = params(filter)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        var content = jdbc.query(SELECT + " LIMIT :limit OFFSET :offset", params, MAPPER);
        var total = jdbc.queryForObject("SELECT COUNT(*) " + FROM, params(filter), Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<ParticipantDTO> findAll(ParticipantFilter filter) {
        return jdbc.query(SELECT, params(filter), MAPPER);
    }

    private static MapSqlParameterSource params(ParticipantFilter filter) {
        return new MapSqlParameterSource("eventId", filter.eventId())
                .addValue("ticketTypeId", filter.ticketTypeId())
                .addValue("status", filter.status() == null ? null : filter.status().name())
                .addValue("name", filter.name() == null ? null : "%" + escapeLike(filter.name()) + "%");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record ParticipantFilter(UUID eventId, UUID ticketTypeId, TicketStatus status, String name) {
    }
}
