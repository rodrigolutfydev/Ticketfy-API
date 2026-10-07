package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CorsIntegrationTest extends IntegrationTestBase {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightAllowsIdempotencyKeyOnOrders() throws Exception {
        mockMvc.perform(options("/orders")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("Idempotency-Key")));
    }

    @Test
    void preflightAllowsPatch() throws Exception {
        mockMvc.perform(options("/users/me/avatar")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void preflightRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/orders")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void rateLimitedLoginExposesRetryAfterToTheBrowser() throws Exception {
        var address = "10.0.8." + (UUID.randomUUID().hashCode() & 0xff);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login")
                            .with(request -> {
                                request.setRemoteAddr(address);
                                return request;
                            })
                            .header("Origin", FRONTEND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}"))
                    .andReturn();
        }

        mockMvc.perform(post("/login")
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .header("Origin", FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }
}
