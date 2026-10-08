package com.lutfy.ticketfy.audit;

import com.lutfy.ticketfy.infra.logging.JobRun;
import com.lutfy.ticketfy.infra.logging.RequestIdFilter;
import com.lutfy.ticketfy.user.User;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(NamedParameterJdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, AuditTargetType targetType, UUID targetId, Map<String, ?> details) {
        var actor = currentActor();
        var params = new MapSqlParameterSource()
                .addValue("actorType", actor == null ? AuditActorType.SYSTEM.name() : AuditActorType.USER.name())
                .addValue("actorId", actor)
                .addValue("action", action.name())
                .addValue("targetType", targetType.name())
                .addValue("targetId", targetId)
                .addValue("details", jsonMapper.writeValueAsString(details))
                .addValue("correlationId", correlationId())
                .addValue("createdAt", Timestamp.from(clock.instant()));
        jdbc.update("""
                INSERT INTO audit_log (actor_type, actor_id, action, target_type, target_id, details,
                                       correlation_id, created_at)
                VALUES (:actorType, :actorId, :action, :targetType, :targetId, CAST(:details AS jsonb),
                        :correlationId, :createdAt)
                """, params);
    }

    private static UUID currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    private static String correlationId() {
        var requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : MDC.get(JobRun.MDC_KEY);
    }
}
