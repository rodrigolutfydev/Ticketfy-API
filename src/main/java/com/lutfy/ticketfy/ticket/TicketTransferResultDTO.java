package com.lutfy.ticketfy.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketTransferResultDTO(UUID ticketId, Instant transferredAt) {
}
