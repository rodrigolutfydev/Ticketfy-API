package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.infra.exception.ProblemReason;
import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.TicketTypeNameAlreadyExistsException;
import com.lutfy.ticketfy.infra.exception.TicketTypeNotFoundException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TicketTypeService {

    private static final String UNIQUE_NAME_CONSTRAINT = "uq_ticket_types_event_name";

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;
    private final Clock clock;

    public TicketTypeService(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
                             EntityManager entityManager, AuditService auditService, Clock clock) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public TicketTypeDetailsDTO create(UUID eventId, TicketTypeCreationDTO dto, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkEventOwnership(event, authenticated);
        checkNotCancelled(event);
        var ticketType = new TicketType(dto, event);
        try {
            var saved = ticketTypeRepository.saveAndFlush(ticketType);
            return new TicketTypeDetailsDTO(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateNameConflict(ex);
        }
    }

    @Transactional
    public TicketTypeDetailsDTO update(UUID eventId, UUID ticketTypeId, TicketTypeUpdateDTO dto, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkEventOwnership(event, authenticated);
        checkNotCancelled(event);
        var ticketType = findOfEvent(eventId, ticketTypeId);

        var previousPrice = ticketType.getPrice();
        ticketType.updateFrom(dto);
        try {
            ticketTypeRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw translateNameConflict(ex);
        }

        if (dto.quantityTotal() != null
                && ticketTypeRepository.changeQuantityTotal(ticketTypeId, dto.quantityTotal()) == 0) {
            entityManager.refresh(ticketType);
            throw new ProblemException(ProblemReason.TICKET_TYPE_BELOW_SOLD, "quantityTotal cannot be lower than the "
                    + ticketType.getQuantitySold() + " tickets already sold or reserved",
                    Map.of("quantitySold", ticketType.getQuantitySold()));
        }

        entityManager.refresh(ticketType);
        if (ticketType.getPrice().compareTo(previousPrice) != 0) {
            auditService.record(AuditAction.TICKET_TYPE_PRICE_CHANGED, AuditTargetType.TICKET_TYPE, ticketTypeId,
                    Map.of("eventId", eventId, "previousPrice", previousPrice, "newPrice", ticketType.getPrice()));
        }
        return new TicketTypeDetailsDTO(ticketType);
    }

    @Transactional
    public TicketTypeDetailsDTO deactivate(UUID eventId, UUID ticketTypeId, User authenticated) {
        return changeActive(eventId, ticketTypeId, false, authenticated);
    }

    @Transactional
    public TicketTypeDetailsDTO reactivate(UUID eventId, UUID ticketTypeId, User authenticated) {
        return changeActive(eventId, ticketTypeId, true, authenticated);
    }

    private TicketTypeDetailsDTO changeActive(UUID eventId, UUID ticketTypeId, boolean active, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkEventOwnership(event, authenticated);
        checkNotCancelled(event);
        var ticketType = findOfEvent(eventId, ticketTypeId);
        var now = clock.instant();
        if (active && !event.getStartsAt().isAfter(now)) {
            throw new ProblemException(ProblemType.SALES_CLOSED,
                    "Ticket sales for this event have ended, so the ticket type cannot be reactivated");
        }
        if (ticketTypeRepository.changeActive(ticketTypeId, active, now) == 0) {
            throw active
                    ? new ProblemException(ProblemReason.TICKET_TYPE_ALREADY_ACTIVE, "Ticket type is already active")
                    : new ProblemException(ProblemReason.TICKET_TYPE_ALREADY_INACTIVE, "Ticket type is already inactive");
        }
        auditService.record(active ? AuditAction.TICKET_TYPE_REACTIVATED : AuditAction.TICKET_TYPE_DEACTIVATED,
                AuditTargetType.TICKET_TYPE, ticketTypeId, Map.of("eventId", eventId, "name", ticketType.getName()));
        return new TicketTypeDetailsDTO(findOfEvent(eventId, ticketTypeId));
    }

    private TicketType findOfEvent(UUID eventId, UUID ticketTypeId) {
        return ticketTypeRepository.findById(ticketTypeId)
                .filter(found -> found.getEvent().getId().equals(eventId))
                .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found"));
    }

    @Transactional(readOnly = true)
    public List<TicketTypeSummaryDTO> listByEvent(UUID eventId) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (event.isCancelled()) {
            return List.of();
        }
        return ticketTypeRepository.findByEventIdAndActiveTrue(eventId)
                .stream()
                .map(TicketTypeSummaryDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketTypeDetailsDTO> listForManagement(UUID eventId, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkEventOwnership(event, authenticated);
        return ticketTypeRepository.findByEventIdOrderByCreatedAtAscNameAsc(eventId)
                .stream()
                .map(TicketTypeDetailsDTO::new)
                .toList();
    }

    private RuntimeException translateNameConflict(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_NAME_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return new TicketTypeNameAlreadyExistsException("A ticket type with this name already exists for this event");
            }
        }
        return ex;
    }

    private void checkNotCancelled(Event event) {
        if (event.isCancelled()) {
            throw new InvalidEventStateException(ProblemReason.EVENT_CANCELLED, "Event was cancelled");
        }
    }

    private void checkEventOwnership(Event event, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!event.getOrganizer().getId().equals(authenticated.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }
}