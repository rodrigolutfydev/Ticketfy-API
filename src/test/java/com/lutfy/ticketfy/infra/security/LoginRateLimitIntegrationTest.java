package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest extends IntegrationTestBase {

    private static final String BAD_LOGIN = "{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksAfterMaxAttemptsPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("/login", "10.0.9.1").andExpect(status().isUnauthorized());
        }

        login("/login", "10.0.9.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ticketfy-api.onrender.com/problems/too-many-login-attempts"))
                .andExpect(jsonPath("$.title").value("Too many login attempts"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Too many login attempts. Try again later."))
                .andExpect(jsonPath("$.instance").value("/login"));

        login("/login", "10.0.9.2").andExpect(status().isUnauthorized());
    }

    @Test
    void encodedLoginPathCountsTowardsTheSameLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("/%6Cogin", "10.0.9.3").andExpect(status().isUnauthorized());
        }

        login("/login", "10.0.9.3").andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String path, String address) throws Exception {
        return mockMvc.perform(post(URI.create(path))
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(BAD_LOGIN));
    }
}
