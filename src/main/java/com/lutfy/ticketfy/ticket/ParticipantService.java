package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventRepository;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class ParticipantService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ParticipantQueryRepository queries;
    private final EventRepository eventRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final ZoneId zone;

    public ParticipantService(ParticipantQueryRepository queries, EventRepository eventRepository,
                              AuditService auditService, Clock clock,
                              @Value("${ticketfy.dashboard.time-zone}") String timeZone) {
        this.queries = queries;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public Page<ParticipantDTO> list(UUID eventId, UUID ticketTypeId, TicketStatus status, String name,
                                     Pageable pageable, User requester) {
        var event = findManagedEvent(eventId, requester);
        return queries.find(filter(event, ticketTypeId, status, name),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }

    @Transactional
    public String exportCsv(UUID eventId, UUID ticketTypeId, TicketStatus status, String name, User requester) {
        var event = findManagedEvent(eventId, requester);
        var filter = filter(event, ticketTypeId, status, name);
        var participants = queries.findAll(filter);
        var details = new LinkedHashMap<String, Object>();
        details.put("rows", participants.size());
        if (filter.ticketTypeId() != null) details.put("ticketTypeId", filter.ticketTypeId());
        if (filter.status() != null) details.put("status", filter.status().name());
        if (filter.name() != null) details.put("nameFilter", true);
        auditService.record(AuditAction.PARTICIPANTS_EXPORTED, AuditTargetType.EVENT, eventId, details);
        return ParticipantCsv.write(participants, zone, requester.getLanguage());
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private Event findManagedEvent(UUID eventId, User requester) {
        var event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (requester.getRole() != Role.ADMIN && !event.getOrganizer().getId().equals(requester.getId())) {
            throw new EventNotFoundException("Event not found");
        }
        return event;
    }

    private static ParticipantQueryRepository.ParticipantFilter filter(Event event, UUID ticketTypeId,
                                                                       TicketStatus status, String name) {
        var trimmed = name == null || name.isBlank() ? null : name.trim();
        if (trimmed != null && trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        return new ParticipantQueryRepository.ParticipantFilter(event.getId(), ticketTypeId, status, trimmed);
    }
}
