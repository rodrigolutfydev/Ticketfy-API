package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Conta criada pelo login com Google, sem senha.
@AutoConfigureMockMvc
class PasswordlessAccountIntegrationTest extends IntegrationTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final String PASSWORD = "Senha-forte-123";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User googleUser;

    @BeforeEach
    void setUp() {
        googleUser = googleUser();
    }

    @Test
    void meTellsTheAccountHasNoPasswordAndIsLinkedToGoogle() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", bearer(googleUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.googleLinked").value(true));

        var withPassword = userWithPassword();
        mockMvc.perform(get("/users/me").header("Authorization", bearer(withPassword)))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.googleLinked").value(false));
    }

    @Test
    void passwordLoginIsRefusedForAnAccountWithoutPassword() throws Exception {
        login(googleUser.getEmail(), "qualquer-senha-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-credentials"));
    }

    @Test
    void setsTheFirstPasswordWithoutTheCurrentOne() throws Exception {
        var oldToken = bearer(googleUser);

        changePassword(googleUser, Map.of("newPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        var user = userRepository.findById(googleUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(user.getGoogleSubject()).isEqualTo(googleUser.getGoogleSubject());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_SET' AND target_id = ?",
                Integer.class, user.getId())).isEqualTo(1);
        mockMvc.perform(get("/users/me").header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());

        // Depois de definida, trocar volta a pedir a senha atual.
        changePassword(user, Map.of("newPassword", "Outra-senha-456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
    }

    @Test
    void accountWithPasswordStillNeedsTheCurrentPassword() throws Exception {
        var user = userWithPassword();

        changePassword(user, Map.of("newPassword", "Outra-senha-456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
        changePassword(user, Map.of("currentPassword", "senha-errada-123", "newPassword", "Outra-senha-456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-password"));
        changePassword(user, Map.of("currentPassword", PASSWORD, "newPassword", "Outra-senha-456"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_CHANGED' AND target_id = ?",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void deletesTheAccountConfirmingWithTheOwnEmail() throws Exception {
        deleteAccount(googleUser, Map.of("confirmation", "EXCLUIR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
        deleteAccount(googleUser, Map.of("email", "outra.pessoa@gmail.com", "confirmation", "EXCLUIR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "email-confirmation-mismatch"));
        assertThat(userRepository.findById(googleUser.getId()).orElseThrow().isDeleted()).isFalse();

        deleteAccount(googleUser, Map.of("email", "  " + googleUser.getEmail().toUpperCase() + " ", "confirmation", "DELETE"))
                .andExpect(status().isNoContent());

        var deleted = userRepository.findById(googleUser.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getGoogleSubject()).isNull();
    }

    @Test
    void accountWithPasswordStillDeletesWithThePassword() throws Exception {
        var user = userWithPassword();

        deleteAccount(user, Map.of("email", user.getEmail(), "confirmation", "EXCLUIR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        deleteAccount(user, Map.of("password", "senha-errada-123", "confirmation", "EXCLUIR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-password"));
        deleteAccount(user, Map.of("password", PASSWORD, "confirmation", "EXCLUIR"))
                .andExpect(status().isNoContent());
    }

    @Test
    void actionsThatConfirmWithPasswordAskToSetOneFirst() throws Exception {
        mockMvc.perform(post("/users/me/data-export")
                        .header("Authorization", bearer(googleUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("password", "qualquer-senha-123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "password-not-set"));
    }

    @Test
    void everyActiveAccountNeedsAPasswordOrGoogle() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (id, name, email, role) VALUES (?, 'Sem', ?, 'USER')",
                UUID.randomUUID(), "sem-" + UUID.randomUUID() + "@mail.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_sign_in_method");
    }

    private User googleUser() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, google_subject, role) VALUES (?, 'Pessoa Google', ?, ?, 'USER')",
                id, "google-" + id + "@gmail.com", "sub-" + id);
        return userRepository.findById(id).orElseThrow();
    }

    private User userWithPassword() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password, role) VALUES (?, 'Com Senha', ?, ?, 'USER')",
                id, "senha-" + id + "@mail.com", passwordEncoder.encode(PASSWORD));
        return userRepository.findById(id).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(fromFrontend())
                .with(request -> {
                    request.setRemoteAddr("passwordless-" + UUID.randomUUID());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions changePassword(User user, Map<String, String> body) throws Exception {
        return mockMvc.perform(patch("/users/me/password")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(new HashMap<>(body))));
    }

    private ResultActions deleteAccount(User user, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/users/me/deletion")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }
}
