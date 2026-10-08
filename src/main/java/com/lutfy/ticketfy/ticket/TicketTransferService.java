package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.InvalidTicketStateException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.TicketNotFoundException;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TicketTransferService {

    private final TicketRepository ticketRepository;
    private final TicketTransferRepository transferRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final TicketTransferPolicy policy;
    private final TransferRecipientLimiter recipientLimiter;
    private final PasswordConfirmation passwordConfirmation;
    private final AuditService auditService;

    public TicketTransferService(TicketRepository ticketRepository, TicketTransferRepository transferRepository,
                                 EventRepository eventRepository, OrderRepository orderRepository,
                                 UserRepository userRepository, TicketService ticketService,
                                 TicketTransferPolicy policy, TransferRecipientLimiter recipientLimiter,
                                 PasswordConfirmation passwordConfirmation, AuditService auditService) {
        this.ticketRepository = ticketRepository;
        this.transferRepository = transferRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketService = ticketService;
        this.policy = policy;
        this.recipientLimiter = recipientLimiter;
        this.passwordConfirmation = passwordConfirmation;
        this.auditService = auditService;
    }

    @Transactional
    public TicketTransferResultDTO transfer(UUID ticketId, TicketTransferRequestDTO dto, User sender) {
        passwordConfirmation.verify(sender, dto.password());
        var refs = ticketRepository.findRefs(ticketId, sender.getId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        var event = eventRepository.findForShareById(refs.eventId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        var now = policy.now();
        if (event.isCancelled()) {
            throw new InvalidEventStateException("Event was cancelled");
        }
        if (!event.getStartsAt().isAfter(now)) {
            throw new ProblemException(ProblemType.TICKET_TRANSFER_CLOSED,
                    "Tickets can only be transferred before the event starts");
        }
        orderRepository.findForUpdateById(refs.orderId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        var ticket = ticketRepository.findById(ticketId)
                .filter(found -> found.getOwner().getId().equals(sender.getId()))
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new InvalidTicketStateException("Only valid tickets can be transferred");
        }
        if (ticket.getTransferCount() >= policy.maxPerTicket()) {
            throw new ProblemException(ProblemType.TICKET_TRANSFER_LIMIT_REACHED,
                    "This ticket reached the limit of " + policy.maxPerTicket() + " transfers");
        }

        var email = dto.recipientEmail().trim();
        if (email.equalsIgnoreCase(sender.getEmail())) {
            throw new ProblemException(ProblemType.SELF_TRANSFER, "You cannot transfer a ticket to yourself");
        }
        recipientLimiter.checkAllowed(sender.getId());
        var recipient = userRepository.findByEmail(email).orElse(null);
        if (recipient == null) {
            recipientLimiter.recordFailure(sender.getId());
            throw new ProblemException(ProblemType.TRANSFER_RECIPIENT_UNAVAILABLE,
                    "The ticket cannot be transferred to this recipient");
        }

        int updated = ticketRepository.transfer(ticketId, sender, recipient, ticketService.generateCode(), now,
                TicketStatus.VALID, policy.maxPerTicket());
        if (updated == 0) {
            throw new InvalidTicketStateException("Ticket is no longer available for transfer");
        }

        transferRepository.saveAndFlush(new TicketTransfer(ticketId, sender.getId(), recipient.getId(), now));
        auditService.record(AuditAction.TICKET_TRANSFERRED, AuditTargetType.TICKET, ticketId, Map.of(
                "fromUserId", sender.getId(),
                "toUserId", recipient.getId(),
                "orderId", refs.orderId(),
                "eventId", refs.eventId(),
                "transferNumber", ticket.getTransferCount() + 1));
        return new TicketTransferResultDTO(ticketId, now);
    }
}
