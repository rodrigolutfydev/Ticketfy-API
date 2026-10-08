package com.lutfy.ticketfy.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class TicketTransferPolicy {

    private final Clock clock;
    private final int maxPerTicket;

    public TicketTransferPolicy(Clock clock, @Value("${ticketfy.ticket-transfer.max-per-ticket}") int maxPerTicket) {
        this.clock = clock;
        this.maxPerTicket = maxPerTicket;
    }

    public Instant now() {
        return clock.instant();
    }

    public int maxPerTicket() {
        return maxPerTicket;
    }

    public boolean allows(Ticket ticket) {
        var event = ticket.getTicketType().getEvent();
        return ticket.getStatus() == TicketStatus.VALID
                && !event.isCancelled()
                && event.getStartsAt().isAfter(now())
                && ticket.getTransferCount() < maxPerTicket;
    }
}
