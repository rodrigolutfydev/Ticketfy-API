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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventImageUrlIntegrationTest extends IntegrationTestBase {

    private static final String CURRENT_IMAGE = "https://cdn.example.com/current.jpg";
    private static final String NEW_IMAGE = "https://cdn.example.com/new.jpg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID organizerId;
    private String token;

    @BeforeEach
    void setUp() {
        organizerId = insertUser("ORGANIZER");
        token = "Bearer " + tokenService.generateToken(userRepository.findById(organizerId).orElseThrow());
    }

    // --- creation -----------------------------------------------------------------------------------------------

    @Test
    void createsWithoutImageWhenImageUrlIsBlankOrAbsent() throws Exception {
        for (var imageField : new String[]{"\"imageUrl\":\"\",", "\"imageUrl\":\"   \",", "\"imageUrl\":null,", ""}) {
            var id = createEvent(imageField)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imageUrl").isEmpty())
                    .andReturn().getResponse().getContentAsString();

            assertThat(storedImage(UUID.fromString(objectMapper.readTree(id).get("id").asString())))
                    .as(imageField).isNull();
        }
    }

    @Test
    void createsWithValidImage() throws Exception {
        createEvent("\"imageUrl\":\"" + NEW_IMAGE + "\",")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(NEW_IMAGE));
    }

    @Test
    void rejectsNonHttpsImageOnCreation() throws Exception {
        createEvent("\"imageUrl\":\"http://cdn.example.com/a.jpg\",")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrl"));
    }

    // --- update -------------------------------------------------------------------------------------------------

    @Test
    void keepsImageWhenImageUrlIsAbsentOrNull() throws Exception {
        var eventId = eventWithImage();

        updateEvent(eventId, "{\"name\":\"Novo nome\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo nome"))
                .andExpect(jsonPath("$.imageUrl").value(CURRENT_IMAGE));
        assertThat(storedImage(eventId)).isEqualTo(CURRENT_IMAGE);

        updateEvent(eventId, "{\"imageUrl\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(CURRENT_IMAGE));
        assertThat(storedImage(eventId)).isEqualTo(CURRENT_IMAGE);
    }

    @Test
    void removesImageWhenImageUrlIsEmptyOrBlank() throws Exception {
        for (var blank : new String[]{"", "   "}) {
            var eventId = eventWithImage();

            updateEvent(eventId, "{\"imageUrl\":\"" + blank + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageUrl").isEmpty());

            assertThat(storedImage(eventId)).as("'%s'", blank).isNull();
        }
    }

    @Test
    void replacesImageWithNewUrl() throws Exception {
        var eventId = eventWithImage();

        updateEvent(eventId, "{\"imageUrl\":\"" + NEW_IMAGE + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(NEW_IMAGE));

        assertThat(storedImage(eventId)).isEqualTo(NEW_IMAGE);
    }

    @Test
    void rejectsNonHttpsImageOnUpdateAndKeepsCurrent() throws Exception {
        var eventId = eventWithImage();

        updateEvent(eventId, "{\"imageUrl\":\"http://cdn.example.com/a.jpg\"}")
                .andExpect(status().isBadRequest());

        assertThat(storedImage(eventId)).isEqualTo(CURRENT_IMAGE);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private ResultActions createEvent(String imageField) throws Exception {
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        var body = """
                {"name":"Show", %s "venueName":"Arena", "address":"Rua A, 100", "city":"Rio de Janeiro",
                 "state":"RJ", "startsAt":"%s"}
                """.formatted(imageField, startsAt);
        return mockMvc.perform(post("/events")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions updateEvent(UUID eventId, String body) throws Exception {
        return mockMvc.perform(put("/events/{id}", eventId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID eventWithImage() {
        var eventId = insertEvent(organizerId);
        jdbc.update("UPDATE events SET image_url = ? WHERE id = ?", CURRENT_IMAGE, eventId);
        return eventId;
    }

    private String storedImage(UUID eventId) {
        return jdbc.queryForObject("SELECT image_url FROM events WHERE id = ?", String.class, eventId);
    }
}
