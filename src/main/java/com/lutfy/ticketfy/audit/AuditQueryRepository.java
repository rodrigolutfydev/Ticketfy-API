package com.lutfy.ticketfy.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditQueryRepository {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public AuditQueryRepository(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public Page<AuditEntryDTO> find(AuditTargetType targetType, UUID targetId, UUID actorId,
                                    Instant from, Instant until, Pageable pageable) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(" WHERE 1 = 1");
        if (targetType != null) {
            where.append(" AND a.target_type = :targetType");
            params.addValue("targetType", targetType.name());
        }
        if (targetId != null) {
            where.append(" AND a.target_id = :targetId");
            params.addValue("targetId", targetId);
        }
        if (actorId != null) {
            where.append(" AND a.actor_id = :actorId");
            params.addValue("actorId", actorId);
        }
        if (from != null) {
            where.append(" AND a.created_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (until != null) {
            where.append(" AND a.created_at < :until");
            params.addValue("until", Timestamp.from(until));
        }

        var total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log a" + where, params, Long.class);
        var sql = """
                SELECT a.id, a.actor_type, a.actor_id, u.name AS actor_name, u.email AS actor_email, a.action,
                       a.target_type, a.target_id, a.details::text AS details, a.correlation_id, a.created_at
                  FROM audit_log a
                  LEFT JOIN users u ON u.id = a.actor_id
                """ + where + """
                 ORDER BY a.created_at DESC, a.id DESC
                 LIMIT :limit OFFSET :offset
                """;
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        var content = jdbc.query(sql, params, (rs, i) -> new AuditEntryDTO(
                rs.getObject("id", UUID.class),
                AuditActorType.valueOf(rs.getString("actor_type")),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"),
                rs.getString("actor_email"),
                AuditAction.valueOf(rs.getString("action")),
                AuditTargetType.valueOf(rs.getString("target_type")),
                rs.getObject("target_id", UUID.class),
                jsonMapper.readValue(rs.getString("details"), DETAILS),
                rs.getString("correlation_id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()));
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
