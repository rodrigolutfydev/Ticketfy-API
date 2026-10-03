package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.infra.exception.InvalidTicketStateException;
import com.lutfy.ticketfy.infra.exception.TicketNotFoundException;
import com.lutfy.ticketfy.order.Order;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class TicketService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ0123456789";
    private static final int CODE_LENGTH = 16;

    private final TicketRepository ticketRepository;
    private final SecureRandom random = new SecureRandom();

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public void issueForOrder(Order order) {
        var tickets = new ArrayList<Ticket>();
        for (var item : order.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                tickets.add(new Ticket(order, item, generateCode()));
            }
        }
        ticketRepository.saveAll(tickets);
    }

    @Transactional(readOnly = true)
    public Page<TicketDetailsDTO> listMyTickets(User authenticated, Pageable pageable) {
        return ticketRepository.findByOwnerId(authenticated.getId(), pageable)
                .map(TicketDetailsDTO::new);
    }

    @Transactional
    public TicketDetailsDTO checkIn(String code, User authenticated) {
        var ticket = ticketRepository.findByCode(code)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        var organizer = ticket.getTicketType().getEvent().getOrganizer();
        if (authenticated.getRole() != Role.ADMIN && !organizer.equals(authenticated)) {
            throw new EventAccessDeniedException("You are not the organizer of this event");
        }

        int updated = ticketRepository.checkIn(code, Instant.now(), TicketStatus.USED, TicketStatus.VALID);
        if (updated == 0) {
            throw new InvalidTicketStateException("Ticket is not valid for check-in");
        }

        var usedTicket = ticketRepository.findByCode(code)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        return new TicketDetailsDTO(usedTicket);
    }

    private String generateCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    @Transactional
    public void cancelForRefund(UUID orderId) {
        long total = ticketRepository.countByOrderId(orderId);
        int cancelled = ticketRepository.cancelValidByOrderId(
                orderId, Instant.now(), TicketStatus.CANCELLED, TicketStatus.VALID);
        if (cancelled != total) {
            throw new InvalidOrderStateException("Order has tickets that were already used");
        }
    }
}