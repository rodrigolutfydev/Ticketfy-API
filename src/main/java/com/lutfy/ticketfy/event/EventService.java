package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventHasSalesException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.InvalidEventDatesException;
import com.lutfy.ticketfy.tickettype.EventPricing;
import com.lutfy.ticketfy.tickettype.TicketTypeRepository;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository repository;
    private final TicketTypeRepository ticketTypeRepository;

    public EventService(EventRepository repository, TicketTypeRepository ticketTypeRepository) {
        this.repository = repository;
        this.ticketTypeRepository = ticketTypeRepository;
    }

    private void validateDates(Instant startsAt, Instant endsAt) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new InvalidEventDatesException("Event end must be after its start");
        }
    }

    @Transactional
    public EventDetailsDTO create(EventCreationDTO dto, User organizer) {
        validateDates(dto.startsAt(), dto.endsAt());
        var event = new Event(dto, organizer);
        var saved = repository.saveAndFlush(event);
        return new EventDetailsDTO(saved);
    }

    @Transactional(readOnly = true)
    public EventDetailsDTO findById(UUID id) {
        return repository.findByIdAndActiveTrue(id)
                .map(EventDetailsDTO::new)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDTO> search(String q, String city, Boolean featured, Boolean soldOut, Pageable pageable) {
        var spec = EventSpecifications.isActive();
        var name = normalize(q);
        if (name != null) spec = spec.and(EventSpecifications.nameContains(name));
        var cityFilter = normalize(city);
        if (cityFilter != null) spec = spec.and(EventSpecifications.cityEquals(cityFilter));
        if (featured != null) spec = spec.and(EventSpecifications.isFeatured(featured));
        if (soldOut != null) spec = spec.and(EventSpecifications.isSoldOut(soldOut));
        return toSummaries(repository.findAll(spec, pageable));
    }

    private Page<EventSummaryDTO> toSummaries(Page<Event> events) {
        var ids = events.map(Event::getId).toList();
        Map<UUID, EventPricing> pricing = ids.isEmpty() ? Map.of() : ticketTypeRepository.findPricingByEventIds(ids)
                .stream()
                .collect(Collectors.toMap(EventPricing::getEventId, Function.identity()));
        return events.map(event -> {
            var eventPricing = pricing.get(event.getId());
            if (eventPricing == null) return new EventSummaryDTO(event, null, false);
            return new EventSummaryDTO(event, eventPricing.getMinPrice(), eventPricing.getAvailableCount() == 0);
        });
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    @Transactional
    public EventDetailsDTO update(UUID id, EventUpdateDTO dto, User requester) {
        var event = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkOwnership(event, requester);
        var newStart = dto.startsAt() != null ? dto.startsAt() : event.getStartsAt();
        var newEnd = dto.endsAt() != null ? dto.endsAt() : event.getEndsAt();
        validateDates(newStart, newEnd);
        event.updateFrom(dto);
        return new EventDetailsDTO(event);
    }

    private void checkOwnership(Event event, User requester) {
        if (requester.getRole() == Role.ADMIN) return;
        if (!event.getOrganizer().getId().equals(requester.getId())) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }

    @Transactional
    public EventDetailsDTO changeFeatured(UUID id, boolean featured) {
        var event = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        event.changeFeatured(featured);
        return new EventDetailsDTO(event);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDTO> listMyEvents(UUID organizerID, Pageable pageable) {
        return toSummaries(repository.findByOrganizerIdAndActiveTrue(organizerID, pageable));
    }

    @Transactional
    public void delete(UUID id, User requester) {
        var event = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkOwnership(event, requester);
        if (ticketTypeRepository.existsByEventIdAndQuantitySoldGreaterThan(id, 0)) {
            throw new EventHasSalesException("Event has sold or reserved tickets");
        }
        event.deactivate();
    }
}
