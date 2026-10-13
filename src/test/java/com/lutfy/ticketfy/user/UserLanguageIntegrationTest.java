package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserLanguageIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = insertUser("USER");
        token = "Bearer " + accessToken(userRepository.findById(userId).orElseThrow());
    }

    @Test
    void defaultsToPortugueseForExistingAndNewAccounts() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"));

        var email = UUID.randomUUID() + "@test.com";
        register("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha-segura\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("pt-BR"));
        assertThat(storedLanguage(email)).isEqualTo("pt-BR");
    }

    @Test
    void registrationAcceptsEnglish() throws Exception {
        var email = UUID.randomUUID() + "@test.com";

        register("{\"name\":\"Ann\",\"email\":\"" + email + "\",\"password\":\"safe-password\",\"language\":\"en\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("en"));

        assertThat(storedLanguage(email)).isEqualTo("en");
    }

    @Test
    void registrationRejectsUnsupportedLanguage() throws Exception {
        var email = UUID.randomUUID() + "@test.com";

        register("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha-segura\",\"language\":\"es\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("language"))
                .andExpect(jsonPath("$.errors[0].code").value("one-of"));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void changesLanguageToEnglishAndBack() throws Exception {
        patchLanguage("{\"language\":\"en\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.language").value("en"));

        mockMvc.perform(get("/users/me").header("Authorization", token))
                .andExpect(jsonPath("$.language").value("en"));
        assertThat(storedLanguage()).isEqualTo("en");

        patchLanguage("{\"language\":\"pt-BR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("pt-BR"));
        assertThat(storedLanguage()).isEqualTo("pt-BR");
    }

    @Test
    void rejectsUnsupportedOrMissingLanguage() throws Exception {
        for (var body : new String[]{"{\"language\":\"es\"}", "{\"language\":\"EN\"}", "{\"language\":\"pt-br\"}"}) {
            patchLanguage(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("language"))
                    .andExpect(jsonPath("$.errors[0].code").value("one-of"))
                    .andExpect(jsonPath("$.errors[0].params.allowed[0]").value("pt-BR"))
                    .andExpect(jsonPath("$.errors[0].params.allowed[1]").value("en"));
        }

        for (var body : new String[]{"{\"language\":null}", "{}"}) {
            patchLanguage(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("language"))
                    .andExpect(jsonPath("$.errors[0].code").value("required"));
        }

        assertThat(storedLanguage()).isEqualTo("pt-BR");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsUnknownLanguage() {
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET language = 'es' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_language");
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patchLanguage(String body) throws Exception {
        return mockMvc.perform(patch("/users/me/language")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String storedLanguage() {
        return jdbc.queryForObject("SELECT language FROM users WHERE id = ?", String.class, userId);
    }

    private String storedLanguage(String email) {
        return jdbc.queryForObject("SELECT language FROM users WHERE email = ?", String.class, email);
    }
}
