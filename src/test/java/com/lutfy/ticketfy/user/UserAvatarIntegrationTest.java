package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserAvatarIntegrationTest extends IntegrationTestBase {

    private static final String AVATAR = "https://cdn.example.com/avatar.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = insertUser("USER");
        token = "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }

    @Test
    void setsAvatarAndReturnsItInMe() throws Exception {
        patchAvatar("{\"avatarUrl\":\"" + AVATAR + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.avatarUrl").value(AVATAR));

        mockMvc.perform(get("/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(AVATAR));

        assertThat(storedAvatar()).isEqualTo(AVATAR);
    }

    @Test
    void blankOrNullRemovesAvatar() throws Exception {
        for (var body : new String[]{"{\"avatarUrl\":\"\"}", "{\"avatarUrl\":\"   \"}", "{\"avatarUrl\":null}", "{}"}) {
            jdbc.update("UPDATE users SET avatar_url = ? WHERE id = ?", AVATAR, userId);

            patchAvatar(body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").doesNotExist());

            assertThat(storedAvatar()).as(body).isNull();
        }
    }

    @Test
    void rejectsNonHttpsUrl() throws Exception {
        patchAvatar("{\"avatarUrl\":\"http://cdn.example.com/avatar.png\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("avatarUrl"));

        assertThat(storedAvatar()).isNull();
    }

    @Test
    void rejectsUrlLongerThan500Characters() throws Exception {
        var longUrl = "https://cdn.example.com/" + "a".repeat(500);

        patchAvatar("{\"avatarUrl\":\"" + longUrl + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(storedAvatar()).isNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me/avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"" + AVATAR + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions patchAvatar(String body) throws Exception {
        return mockMvc.perform(patch("/users/me/avatar")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String storedAvatar() {
        return jdbc.queryForObject("SELECT avatar_url FROM users WHERE id = ?", String.class, userId);
    }
}
