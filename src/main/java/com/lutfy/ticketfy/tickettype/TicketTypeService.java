package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.InvalidTicketTypeQuantityException;
import com.lutfy.ticketfy.infra.exception.TicketTypeNameAlreadyExistsException;
import com.lutfy.ticketfy.infra.exception.TicketTypeNotFoundException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketTypeService {

    private static final String UNIQUE_NAME_CONSTRAINT = "uq_ticket_types_event_name";

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final EntityManager entityManager;

    public TicketTypeService(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
                             EntityManager entityManager) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
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
        var ticketType = ticketTypeRepository.findByIdAndActiveTrue(ticketTypeId)
                .filter(found -> found.getEvent().getId().equals(eventId))
                .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found"));

        ticketType.updateFrom(dto);
        try {
            ticketTypeRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw translateNameConflict(ex);
        }

        if (dto.quantityTotal() != null
                && ticketTypeRepository.changeQuantityTotal(ticketTypeId, dto.quantityTotal()) == 0) {
            entityManager.refresh(ticketType);
            throw new InvalidTicketTypeQuantityException("quantityTotal cannot be lower than the "
                    + ticketType.getQuantitySold() + " tickets already sold or reserved");
        }

        entityManager.refresh(ticketType);
        return new TicketTypeDetailsDTO(ticketType);
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
            throw new InvalidEventStateException("Event was cancelled");
        }
    }

    private void checkEventOwnership(Event event, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!event.getOrganizer().getId().equals(authenticated.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }
}