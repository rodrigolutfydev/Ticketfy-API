package com.lutfy.ticketfy.infra.exception;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void malformedJsonReturnsGenericBadRequest() throws Exception {
        var body = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("JSON parse").doesNotContain("line:", "jackson", "Exception");
    }

    @Test
    void missingBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/users/me/avatar")
                        .header("Authorization", tokenFor(insertUser("USER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void wrongFieldTypeReturnsBadRequest() throws Exception {
        var body = mockMvc.perform(post("/orders")
                        .header("Authorization", tokenFor(insertUser("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ticketTypeId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"abc\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Integer", "abc");
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Ana"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported media type"));
    }

    @Test
    void loginRespondsTheSameForUnknownEmailAndWrongPassword() throws Exception {
        var email = "login." + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senhaCorreta123\"}"))
                .andExpect(status().isCreated());

        var wrongPassword = mockMvc.perform(post("/login").with(remoteAddr("10.0.7.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"senhaErrada123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        var unknownEmail = mockMvc.perform(post("/login").with(remoteAddr("10.0.7.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody." + UUID.randomUUID() + "@mail.com\",\"password\":\"senhaErrada123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword).isEqualTo("{\"message\":\"Invalid credentials\"}");

        mockMvc.perform(post("/login").with(remoteAddr("10.0.7.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"senhaCorreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private static RequestPostProcessor remoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }
}
