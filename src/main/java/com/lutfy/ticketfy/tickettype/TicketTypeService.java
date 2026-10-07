package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.TicketTypeNameAlreadyExistsException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
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

    public TicketTypeService(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public TicketTypeDetailsDTO create(UUID eventId, TicketTypeCreationDTO dto, User authenticated) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkEventOwnership(event, authenticated);
        var ticketType = new TicketType(dto, event);
        try {
            var saved = ticketTypeRepository.saveAndFlush(ticketType);
            return new TicketTypeDetailsDTO(saved);
        } catch (DataIntegrityViolationException ex) {
            throw translateNameConflict(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<TicketTypeSummaryDTO> listByEvent(UUID eventId) {
        eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
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

    // Only the (event_id, name) unique constraint becomes a 409; any other violation is rethrown as is
    private RuntimeException translateNameConflict(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_NAME_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return new TicketTypeNameAlreadyExistsException("A ticket type with this name already exists for this event");
            }
        }
        return ex;
    }

    private void checkEventOwnership(Event event, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!event.getOrganizer().getId().equals(authenticated.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }
}