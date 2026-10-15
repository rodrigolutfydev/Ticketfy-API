package com.lutfy.ticketfy.event;

import java.util.UUID;

public record EventCancellationPreviewDTO(
        UUID eventId,
        int ordersToRefund,
        int freeOrdersToCancel,
        int pendingOrdersToCancel,
        int ordersRequiringManualAction,
        int usedTickets
) {
}
