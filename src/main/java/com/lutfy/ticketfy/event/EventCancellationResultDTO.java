package com.lutfy.ticketfy.event;

import java.time.Instant;
import java.util.UUID;

public record EventCancellationResultDTO(
        UUID eventId,
        Instant cancelledAt,
        int refundedOrders,
        int cancelledFreeOrders,
        int cancelledOrders,
        int pendingOrders,
        int requiresManualAction,
        int usedTickets
) {
}
