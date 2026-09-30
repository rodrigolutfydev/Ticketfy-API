package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketTypeService {

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
        var saved = ticketTypeRepository.saveAndFlush(ticketType);
        return new TicketTypeDetailsDTO(saved);
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

    private void checkEventOwnership(Event event, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!event.getOrganizer().getId().equals(authenticated.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }
}