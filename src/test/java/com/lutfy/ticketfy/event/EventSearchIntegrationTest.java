package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventSearchIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    // Unique tag per test so events from other tests never match the filters
    private String tag;
    private UUID rockRio;
    private UUID rockSaoPaulo;
    private UUID jazzSaoPaulo;
    private UUID percent;
    private UUID underscore;
    private UUID lookalike;

    @BeforeEach
    void createEvents() {
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        var organizer = insertUser("ORGANIZER");
        rockRio = insertEvent(organizer, "Show de Rock " + tag, "Rio de Janeiro " + tag, true);
        rockSaoPaulo = insertEvent(organizer, "Noite do rock " + tag, "Sao Paulo " + tag, true);
        jazzSaoPaulo = insertEvent(organizer, "Jazz " + tag, "Sao Paulo " + tag, true);
        percent = insertEvent(organizer, "Desconto " + tag + " 100% off", "Recife " + tag, true);
        underscore = insertEvent(organizer, "Festa " + tag + " a_b", "Recife " + tag, true);
        insertEvent(organizer, "Rock cancelado " + tag, "Rio de Janeiro " + tag, false);
        // would match "a_b" if "_" were a wildcard
        lookalike = insertEvent(organizer, "Festa " + tag + " ayb", "Recife " + tag, true);
    }

    // Pairs of parameter name and raw value, so "%" and "_" reach the API unencoded by the test
    private ResultActions search(String... params) throws Exception {
        var request = get("/events");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private UUID insertEvent(UUID organizer, String name, String city, boolean active) {
        var id = UUID.randomUUID();
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        jdbc.update("""
                INSERT INTO events (id, name, venue_name, address, city, state, starts_at, organizer_id, active)
                VALUES (?, ?, 'Arena', 'Rua A, 100', ?, 'SP', ?, ?, ?)
                """, id, name, city, Timestamp.from(startsAt), organizer, active);
        return id;
    }

    @Test
    void searchesByNameIgnoringCase() throws Exception {
        search("q", "ROCK " + tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(rockRio.toString(), rockSaoPaulo.toString())));
    }

    @Test
    void treatsLikeWildcardsInNameAsText() throws Exception {
        search("q", tag + " 100%")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(percent.toString())));
        search("q", tag + " a_b")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(underscore.toString())));
        search("q", tag + "%")
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void searchesByCityIgnoringCase() throws Exception {
        search("city", "sAO pAULO " + tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(rockSaoPaulo.toString(), jazzSaoPaulo.toString())));
    }

    @Test
    void cityMustMatchExactly() throws Exception {
        search("city", "Paulo " + tag)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void combinesNameAndCity() throws Exception {
        search("q", "rock " + tag, "city", "Sao Paulo " + tag)
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(rockSaoPaulo.toString())));
    }

    @Test
    void ignoresBlankFilters() throws Exception {
        search("q", " ", "city", " ", "size", "100", "sort", "createdAt,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + rockRio + "')]").exists());
    }

    @Test
    void neverListsInactiveEvents() throws Exception {
        search("q", tag, "size", "100")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        rockRio.toString(), rockSaoPaulo.toString(), jazzSaoPaulo.toString(),
                        percent.toString(), underscore.toString(), lookalike.toString())));
    }
}