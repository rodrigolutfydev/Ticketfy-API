package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.ClockedIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventSalesWindowIntegrationTest extends ClockedIntegrationTestBase {

    private String tag;
    private Instant startsAt;
    private UUID show;
    private UUID openEnded;
    private UUID nextDay;

    @BeforeEach
    void setUpEvents() {
        tag = "w" + UUID.randomUUID().toString().substring(0, 8);
        startsAt = futureStart();
        var organizer = insertUser("ORGANIZER");
        show = event(organizer, "Show " + tag, startsAt, startsAt.plus(Duration.ofHours(3)), true);
        openEnded = event(organizer, "Palestra " + tag, startsAt, null, false);
        nextDay = event(organizer, "Festival " + tag, startsAt.plus(Duration.ofDays(1)), null, true);
    }

    @Test
    void upcomingEventsAreListedWithSalesOpen() throws Exception {
        travelTo(startsAt.minus(Duration.ofMinutes(1)));

        search("q", tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        show.toString(), openEnded.toString(), nextDay.toString())));
        mockMvc.perform(get("/events/{id}", show))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesClosed").value(false));
    }

    @Test
    void eventInProgressStaysInSearchWithSalesClosed() throws Exception {
        travelTo(startsAt.plus(Duration.ofHours(1)));

        search("q", tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(show.toString(), nextDay.toString())));
        mockMvc.perform(get("/events/{id}", show))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesClosed").value(true));
    }

    @Test
    void endedEventLeavesSearchButItsPageStaysAvailable() throws Exception {
        travelTo(startsAt.plus(Duration.ofHours(4)));

        search("q", tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(nextDay.toString())));
        mockMvc.perform(get("/events/{id}", show))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesClosed").value(true));
        mockMvc.perform(get("/events/{id}/ticket-types", show))
                .andExpect(status().isOk());
    }

    @Test
    void featuredOnlyListsEventsThatHaveNotStarted() throws Exception {
        travelTo(startsAt.minus(Duration.ofMinutes(1)));
        search("q", tag, "featured", "true")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(show.toString(), nextDay.toString())));

        travelTo(startsAt);
        search("q", tag, "featured", "true")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(nextDay.toString())));
        search("q", tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(show.toString(), nextDay.toString())));
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/events");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }
}
