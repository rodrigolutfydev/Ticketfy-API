package com.lutfy.ticketfy.ticket;

import java.time.Instant;
import java.util.UUID;

public record ParticipantDTO(
        UUID ticketId,
        UUID ticketTypeId,
        String ticketTypeName,
        TicketStatus status,
        Instant checkedInAt,
        boolean transferred,
        String holderName,
        String codeSuffix
) {
}
