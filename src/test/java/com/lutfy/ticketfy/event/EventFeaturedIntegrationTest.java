package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventFeaturedIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private UUID organizerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        organizerId = insertUser("ORGANIZER");
        eventId = insertEvent(organizerId);
    }

    @Test
    void adminFeaturesAndUnfeaturesEvent() throws Exception {
        var admin = tokenFor(insertUser("ADMIN"));

        changeFeatured(eventId, admin, "{\"featured\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.featured").value(true));
        assertThat(storedFeatured(eventId)).isTrue();

        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.featured").value(true));

        changeFeatured(eventId, admin, "{\"featured\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(false));
        assertThat(storedFeatured(eventId)).isFalse();
    }

    @Test
    void newEventsAreNotFeatured() throws Exception {
        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.featured").value(false));
    }

    @Test
    void onlyAdminCanChangeFeatured() throws Exception {
        changeFeatured(eventId, tokenFor(organizerId), "{\"featured\":true}")
                .andExpect(status().isForbidden());
        changeFeatured(eventId, tokenFor(insertUser("USER")), "{\"featured\":true}")
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/featured", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featured\":true}"))
                .andExpect(status().isUnauthorized());

        assertThat(storedFeatured(eventId)).isFalse();
    }

    @Test
    void returnsNotFoundForUnknownOrInactiveEvent() throws Exception {
        var admin = tokenFor(insertUser("ADMIN"));
        jdbc.update("UPDATE events SET active = false WHERE id = ?", eventId);

        changeFeatured(UUID.randomUUID(), admin, "{\"featured\":true}").andExpect(status().isNotFound());
        changeFeatured(eventId, admin, "{\"featured\":true}").andExpect(status().isNotFound());
    }

    @Test
    void requiresFeaturedField() throws Exception {
        changeFeatured(eventId, tokenFor(insertUser("ADMIN")), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("featured"));
    }

    @Test
    void filtersListingByFeatured() throws Exception {
        var tag = "f" + UUID.randomUUID().toString().substring(0, 8);
        var featuredRio = taggedEvent(tag, "Rio", true);
        var featuredRecife = taggedEvent(tag, "Recife", true);
        var regularRio = taggedEvent(tag, "Rio", false);

        mockMvc.perform(get("/events").param("q", tag).param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(featuredRio.toString(), featuredRecife.toString())))
                .andExpect(jsonPath("$.content[0].featured").value(true));

        mockMvc.perform(get("/events").param("q", tag).param("featured", "false"))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(regularRio.toString())))
                .andExpect(jsonPath("$.content[0].featured").value(false));

        mockMvc.perform(get("/events").param("q", tag).param("city", "rio").param("featured", "true"))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(featuredRio.toString())));

        mockMvc.perform(get("/events").param("q", tag))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        featuredRio.toString(), featuredRecife.toString(), regularRio.toString())));
    }

    @Test
    void rejectsInvalidFeaturedFilter() throws Exception {
        mockMvc.perform(get("/events").param("featured", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'featured'"));
    }

    private UUID taggedEvent(String tag, String city, boolean featured) {
        var id = insertEvent(organizerId);
        jdbc.update("UPDATE events SET name = ?, city = ?, featured = ? WHERE id = ?",
                "Evento " + tag + " " + UUID.randomUUID(), city, featured, id);
        return id;
    }

    private ResultActions changeFeatured(UUID id, String token, String body) throws Exception {
        return mockMvc.perform(patch("/events/{id}/featured", id)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Boolean storedFeatured(UUID id) {
        return jdbc.queryForObject("SELECT featured FROM events WHERE id = ?", Boolean.class, id);
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }
}
