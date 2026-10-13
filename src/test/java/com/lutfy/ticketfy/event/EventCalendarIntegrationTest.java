package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventCalendarIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private UUID organizerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        organizerId = insertUser("ORGANIZER");
        eventId = insertEvent(organizerId);
    }

    @Test
    void servesAnRfc5545FileWithCrlfFoldedLinesAndUtcTimes() throws Exception {
        var description = "Uma noite de música brasileira, com repertório de samba, choro e bossa nova. ".repeat(4).trim();
        jdbc.update("UPDATE events SET description = ? WHERE id = ?", description, eventId);
        var startsAt = jdbc.queryForObject("SELECT starts_at FROM events WHERE id = ?", Timestamp.class, eventId).toInstant();

        var response = mockMvc.perform(get("/events/{id}/calendar.ics", eventId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/calendar;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ticketfy-event-" + eventId + ".ics\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andReturn().getResponse();
        var body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(body).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VEVENT\r\nEND:VCALENDAR\r\n");
        assertThat(body.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
        assertThat(Arrays.stream(body.split("\r\n"))).allMatch(line -> line.getBytes(StandardCharsets.UTF_8).length <= 75);
        assertThat(body).contains("\r\n ");

        var lines = unfold(body);
        assertThat(lines).contains(
                "UID:event-" + eventId + "@ticketfy",
                "DTSTART:" + utc(startsAt),
                "DTEND:" + utc(startsAt.plus(3, ChronoUnit.HOURS)),
                "SUMMARY:Test Event",
                "LOCATION:Test Arena\\, Rua A\\, 100\\, Rio de Janeiro - RJ",
                "STATUS:CONFIRMED",
                "URL:http://localhost:5173/events/" + eventId);
        assertThat(value(lines, "DESCRIPTION")).isEqualTo(
                description.replace(",", "\\,") + "\\n\\nhttp://localhost:5173/events/" + eventId);
        assertThat(value(lines, "DTSTAMP")).matches("\\d{8}T\\d{6}Z");
    }

    @Test
    void organizerTextCannotCreateNewProperties() throws Exception {
        var name = "Show\r\nATTENDEE;ROLE=CHAIR:mailto:x@example.com\nEND:VEVENT; fim, \\ ok";
        jdbc.update("UPDATE events SET name = ?, description = ? WHERE id = ?", name, "linha 1\nBEGIN:VALARM", eventId);

        var lines = unfold(download(eventId));

        assertThat(lines).noneMatch(line -> line.startsWith("ATTENDEE") || line.startsWith("BEGIN:VALARM"));
        assertThat(lines.stream().filter(line -> line.equals("END:VEVENT"))).hasSize(1);
        assertThat(value(lines, "SUMMARY"))
                .isEqualTo("Show\\nATTENDEE\\;ROLE=CHAIR:mailto:x@example.com\\nEND:VEVENT\\; fim\\, \\\\ ok");
        assertThat(value(lines, "DESCRIPTION")).startsWith("linha 1\\nBEGIN:VALARM\\n\\n");
    }

    @Test
    void eventWithoutEndHasNoDtend() throws Exception {
        jdbc.update("UPDATE events SET ends_at = NULL WHERE id = ?", eventId);

        var lines = unfold(download(eventId));

        assertThat(lines).anyMatch(line -> line.startsWith("DTSTART:"));
        assertThat(lines).noneMatch(line -> line.startsWith("DTEND"));
    }

    @Test
    void cancelledEventIsServedAsCancelledWithAHigherSequence() throws Exception {
        var created = Instant.now().minus(1, ChronoUnit.HOURS);
        jdbc.update("UPDATE events SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(created), Timestamp.from(created), eventId);
        var before = unfold(download(eventId));
        assertThat(value(before, "SEQUENCE")).isEqualTo("0");

        var organizer = userRepository.findById(organizerId).orElseThrow();
        mockMvc.perform(post("/events/{id}/cancel", eventId)
                        .header("Authorization", "Bearer " + accessToken(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Chuva forte\"}"))
                .andExpect(status().isOk());

        var after = unfold(download(eventId));
        assertThat(after).contains("STATUS:CANCELLED", "UID:event-" + eventId + "@ticketfy");
        assertThat(Long.parseLong(value(after, "SEQUENCE"))).isGreaterThanOrEqualTo(3500);
        assertThat(value(after, "DESCRIPTION")).startsWith("CANCELLED: Chuva forte\\n\\n");
    }

    @Test
    void unknownOrDeactivatedEventIsNotFound() throws Exception {
        jdbc.update("UPDATE events SET active = false WHERE id = ?", eventId);

        for (var id : List.of(UUID.randomUUID(), eventId)) {
            mockMvc.perform(get("/events/{id}/calendar.ics", id).accept(new MediaType("text", "calendar")))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                    .andExpect(jsonPath("$.type").value(endsWith("/event-not-found")));
        }
    }

    private String download(UUID id) throws Exception {
        var bytes = mockMvc.perform(get("/events/{id}/calendar.ics", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> unfold(String ics) {
        return Arrays.asList(ics.replace("\r\n ", "").split("\r\n"));
    }

    private static String value(List<String> lines, String name) {
        return lines.stream()
                .filter(line -> line.startsWith(name + ":"))
                .findFirst()
                .map(line -> line.substring(name.length() + 1))
                .orElseThrow();
    }

    private static String utc(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "");
    }
}
