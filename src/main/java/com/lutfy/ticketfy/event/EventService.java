package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.infra.exception.EventAccessDeniedException;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.infra.exception.InvalidEventDatesException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    private void validateDates(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidEventDatesException("Event end must be after its start");
        }
    }

    @Transactional
    public EventDetailsDTO create(EventCreationDTO dto, User organizer) {
        validateDates(dto.startsAt(), dto.endsAt());
        var event = new Event(dto, organizer);
        var saved = repository.save(event);
        return new EventDetailsDTO(saved);
    }

    @Transactional(readOnly = true)
    public EventDetailsDTO findById(UUID id) {
        return repository.findByIdAndActiveTrue(id)
                .map(EventDetailsDTO::new)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDTO> search(String q, String city, Pageable pageable) {
        var name = normalize(q);
        var cityFilter = normalize(city);

        Page<Event> page;
        if (name != null && cityFilter != null) {
            page = repository.findByActiveTrueAndNameContainingIgnoreCaseAndCityIgnoreCase(name, cityFilter, pageable);
        } else if (name != null) {
            page = repository.findByActiveTrueAndNameContainingIgnoreCase(name, pageable);
        } else if (cityFilter != null) {
            page = repository.findByActiveTrueAndCityIgnoreCase(cityFilter, pageable);
        } else {
            page = repository.findByActiveTrue(pageable);
        }
        return page.map(EventSummaryDTO::new);
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
        if (!event.getOrganizer().equals(requester)) {
            throw new EventAccessDeniedException("You do not own this event");
        }
    }

    @Transactional
    public void delete(UUID id, User requester) {
        var event = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        checkOwnership(event, requester);
        event.deactivate();
    }
}
