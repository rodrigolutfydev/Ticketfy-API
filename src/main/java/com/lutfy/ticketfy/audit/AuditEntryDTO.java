package com.lutfy.ticketfy.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryDTO(
        UUID id,
        AuditActorType actorType,
        UUID actorId,
        String actorName,
        String actorEmail,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        Map<String, Object> details,
        String correlationId,
        Instant createdAt
) {}
