package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.infra.calendar.IcsWriter;
import com.lutfy.ticketfy.infra.config.SiteSettings;
import com.lutfy.ticketfy.infra.exception.EventNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

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
        return calendar(event, null);
    }

    /**
     * Mesmo arquivo do endpoint. Com {@code defaultDuration}, evento sem
     * término ganha DTEND = início + duração (anexo do e-mail de compra);
     * sem ela, sai sem DTEND, como em GET /events/{id}/calendar.ics.
     */
    public String calendar(Event event, Duration defaultDuration) {
        var url = site.eventUrl(event.getId()).orElse(null);
        var endsAt = event.getEndsAt() != null || defaultDuration == null
                ? event.getEndsAt()
                : event.getStartsAt().plus(defaultDuration);
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
                .time("DTEND", endsAt)
                .text("SUMMARY", event.getName())
                .text("LOCATION", event.fullLocation())
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
