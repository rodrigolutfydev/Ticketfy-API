package com.lutfy.ticketfy.tickettype;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TicketTypeNameConflictIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private UUID eventId;
    private String token;

    @BeforeEach
    void setUp() {
        var organizerId = insertUser("ORGANIZER");
        eventId = insertEvent(organizerId);
        token = "Bearer " + tokenService.generateToken(userRepository.findById(organizerId).orElseThrow());
    }

    @Test
    void creatingTicketTypeWithExistingNameReturnsConflict() throws Exception {
        create(eventId, "Pista").andExpect(status().isCreated());

        create(eventId, "Pista")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A ticket type with this name already exists for this event"));

        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_types WHERE event_id = ? AND name = 'Pista'", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sameNameIsAllowedInAnotherEvent() throws Exception {
        create(eventId, "Pista").andExpect(status().isCreated());

        var otherEvent = insertEvent(jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID.class, eventId));
        create(otherEvent, "Pista").andExpect(status().isCreated());
    }

    private ResultActions create(UUID event, String name) throws Exception {
        return mockMvc.perform(post("/events/{id}/ticket-types", event)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":80.00,\"quantityTotal\":100}"));
    }
}
