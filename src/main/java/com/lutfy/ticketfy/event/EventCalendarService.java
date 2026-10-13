package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.infra.calendar.IcsWriter;
import com.lutfy.ticketfy.infra.config.SiteSettings;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EventCalendarService {

    static final String PRODID = "-//Ticketfy//Events//EN";

    private final EventRepository repository;
    private final SiteSettings site;
    private final Clock clock;

    public EventCalendarService(EventRepository repository, SiteSettings site, Clock clock) {
        this.repository = repository;
        this.site = site;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public String calendar(UUID id) {
        var event = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        var url = site.eventUrl(event.getId()).orElse(null);
        var writer = new IcsWriter()
                .raw("BEGIN", "VCALENDAR")
                .raw("VERSION", "2.0")
                .raw("PRODID", PRODID)
                .raw("CALSCALE", "GREGORIAN")
                .raw("METHOD", "PUBLISH")
                .raw("BEGIN", "VEVENT")
                .raw("UID", uid(event.getId()))
                .time("DTSTAMP", clock.instant())
                .raw("SEQUENCE", String.valueOf(sequence(event)))
                .time("LAST-MODIFIED", event.getUpdatedAt())
                .time("DTSTART", event.getStartsAt())
                .time("DTEND", event.getEndsAt())
                .text("SUMMARY", event.getName())
                .text("LOCATION", location(event))
                .text("DESCRIPTION", description(event, url))
                .raw("STATUS", event.isCancelled() ? "CANCELLED" : "CONFIRMED");
        if (url != null) {
            writer.raw("URL", url);
        }
        return writer.raw("END", "VEVENT").raw("END", "VCALENDAR").build();
    }

    static String uid(UUID eventId) {
        return "event-" + eventId + "@ticketfy";
    }

    static long sequence(Event event) {
        if (event.getCreatedAt() == null || event.getUpdatedAt() == null) return 0;
        return Math.max(0, Duration.between(event.getCreatedAt(), event.getUpdatedAt()).toSeconds());
    }

    private static String location(Event event) {
        var cityAndState = Stream.of(event.getCity(), event.getState())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" - "));
        return Stream.of(event.getVenueName(), event.getAddress(), cityAndState)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String description(Event event, String url) {
        var parts = new StringBuilder();
        if (event.isCancelled()) {
            parts.append("CANCELLED");
            if (event.getCancellationReason() != null && !event.getCancellationReason().isBlank()) {
                parts.append(": ").append(event.getCancellationReason().trim());
            }
            parts.append("\n\n");
        }
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            parts.append(event.getDescription().trim()).append("\n\n");
        }
        if (url != null) {
            parts.append(url);
        }
        return parts.toString().trim();
    }
}
