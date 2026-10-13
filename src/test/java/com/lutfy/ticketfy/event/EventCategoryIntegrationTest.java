package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.ClockedIntegrationTestBase;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventCategoryIntegrationTest extends ClockedIntegrationTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String tag;
    private Instant startsAt;
    private User organizer;

    @BeforeEach
    void setUp() {
        tag = "c" + UUID.randomUUID().toString().substring(0, 8);
        startsAt = futureStart();
        organizer = user("ORGANIZER");
    }

    @Test
    void creationRequiresACategory() throws Exception {
        createEvent(null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("category")));
        createEvent("\"TALKS\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("TALKS"));
        createEvent("\"CONCERTS\"")
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingChangesOrKeepsTheCategory() throws Exception {
        var id = categorized("Show", "MUSIC", false);

        update(id, "{\"category\":\"COMEDY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("COMEDY"));
        update(id, "{\"name\":\"Show renomeado " + tag + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("COMEDY"));
        mockMvc.perform(get("/events/{id}", id)).andExpect(jsonPath("$.category").value("COMEDY"));
    }

    @Test
    void searchFiltersByOneOrMoreCategoriesCombinedWithOtherFilters() throws Exception {
        var jazz = categorized("Jazz", "MUSIC", true);
        var rock = categorized("Rock", "MUSIC", false);
        var standUp = categorized("Stand-up", "COMEDY", false);
        categorized("Peça", "THEATER", false);

        search("q", tag, "category", "MUSIC")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(jazz.toString(), rock.toString())))
                .andExpect(jsonPath("$.content[*].category").value(contains("MUSIC", "MUSIC")));
        search("q", tag, "category", "MUSIC", "category", "COMEDY")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        jazz.toString(), rock.toString(), standUp.toString())));
        search("q", tag, "category", "MUSIC,COMEDY")
                .andExpect(jsonPath("$.content.length()").value(3));
        search("q", "jazz " + tag, "category", "MUSIC")
                .andExpect(jsonPath("$.content[*].id").value(contains(jazz.toString())));
        search("q", tag, "category", "MUSIC", "featured", "true")
                .andExpect(jsonPath("$.content[*].id").value(contains(jazz.toString())));
        search("q", tag, "city", "Rio de Janeiro", "category", "COMEDY")
                .andExpect(jsonPath("$.content[*].id").value(contains(standUp.toString())));
        search("q", tag, "category", "SPORTS")
                .andExpect(jsonPath("$.content.length()").value(0));
        search("q", tag)
                .andExpect(jsonPath("$.content.length()").value(4));
    }

    @Test
    void invalidCategoryIsRejected() throws Exception {
        mockMvc.perform(get("/events").param("category", "CONCERTS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-parameter"));
        mockMvc.perform(get("/events").param("category", "music"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countsOnlyEventsVisibleInTheShowcase() throws Exception {
        var before = counts();

        categorized("Visível 1", "KIDS", false);
        categorized("Visível 2", "KIDS", true);
        var inactive = categorized("Inativo", "KIDS", false);
        jdbc.update("UPDATE events SET active = false WHERE id = ?", inactive);
        var cancelled = categorized("Cancelado", "KIDS", false);
        jdbc.update("UPDATE events SET cancelled_at = NOW() WHERE id = ?", cancelled);
        var ended = event(organizer.getId(), "Terminado " + tag, Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(1)), false);
        jdbc.update("UPDATE events SET category = 'KIDS' WHERE id = ?", ended);
        var suspendedOrganizer = user("ORGANIZER");
        var hidden = event(suspendedOrganizer.getId(), "Suspenso " + tag, startsAt, null, false);
        jdbc.update("UPDATE events SET category = 'KIDS' WHERE id = ?", hidden);
        jdbc.update("UPDATE users SET suspended_at = NOW(), suspension_reason = 'x', suspended_by = ? WHERE id = ?",
                organizer.getId(), suspendedOrganizer.getId());

        var after = counts();

        assertThat(after.get("KIDS") - before.get("KIDS")).isEqualTo(2);
        assertThat(after.keySet()).containsExactlyElementsOf(
                Arrays.stream(EventCategory.values()).map(Enum::name).toList());
        for (var category : EventCategory.values()) {
            var total = body(search("category", category.name(), "size", "1")).get("page").get("totalElements").asLong();
            assertThat(after.get(category.name())).as(category.name()).isEqualTo(total);
        }
    }

    private UUID categorized(String name, String category, boolean featured) {
        var id = event(organizer.getId(), name + " " + tag, startsAt, startsAt.plus(Duration.ofHours(3)), featured);
        jdbc.update("UPDATE events SET category = ? WHERE id = ?", category, id);
        return id;
    }

    private ResultActions createEvent(String category) throws Exception {
        var body = """
                {"name":"Palestra %s", "venueName":"Auditório", "address":"Rua B, 10", "city":"Recife",
                 "state":"PE", "startsAt":"%s"%s}
                """.formatted(tag, startsAt.plus(Duration.ofDays(1)), category == null ? "" : ", \"category\":" + category);
        return mockMvc.perform(post("/events")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions update(UUID id, String body) throws Exception {
        return mockMvc.perform(put("/events/{id}", id)
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/events");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private Map<String, Long> counts() throws Exception {
        var counts = new LinkedHashMap<String, Long>();
        for (var entry : body(mockMvc.perform(get("/events/categories")).andExpect(status().isOk()))) {
            counts.put(entry.get("category").asString(), entry.get("events").asLong());
        }
        return counts;
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
