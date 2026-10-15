package com.lutfy.ticketfy.notification;

import java.util.UUID;

public record TicketTransferredEvent(UUID ticketId, UUID senderId, UUID recipientId) {
}
