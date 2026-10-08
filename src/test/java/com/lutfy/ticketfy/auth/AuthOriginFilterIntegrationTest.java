package com.lutfy.ticketfy.auth;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.AuthOriginFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthOriginFilterIntegrationTest extends IntegrationTestBase {

    private static final String PROBLEM = "https://ticketfy-api.onrender.com/problems/auth-request-rejected";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAForeignOrigin() throws Exception {
        expectRejected(post("/auth/refresh").header("Origin", "https://evil.example").header(AuthOriginFilter.HEADER, "1"));
    }

    @Test
    void rejectsAPreviewDeploymentOfAnAllowedOrigin() throws Exception {
        expectRejected(post("/auth/login").header("Origin", "http://preview.localhost:5173")
                .header(AuthOriginFilter.HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"password\":\"whatever-123\"}"));
    }

    @Test
    void rejectsAMissingOrigin() throws Exception {
        expectRejected(post("/auth/logout").header(AuthOriginFilter.HEADER, "1"));
    }

    @Test
    void rejectsAMissingAuthHeader() throws Exception {
        expectRejected(post("/auth/refresh").header("Origin", FRONTEND_ORIGIN));
    }

    @Test
    void rejectsAWrongAuthHeaderValue() throws Exception {
        expectRejected(post("/auth/logout").header("Origin", FRONTEND_ORIGIN).header(AuthOriginFilter.HEADER, "true"));
    }

    @Test
    void appliesToEncodedPaths() throws Exception {
        expectRejected(post(URI.create("/auth/%6Cogout")).header("Origin", "https://evil.example")
                .header(AuthOriginFilter.HEADER, "1"));
    }

    @Test
    void protectsLogoutAll() throws Exception {
        expectRejected(post("/auth/logout-all").header("Origin", "https://evil.example").header(AuthOriginFilter.HEADER, "1"));
    }

    @Test
    void letsTheFrontendThrough() throws Exception {
        mockMvc.perform(post("/auth/logout").with(fromFrontend())).andExpect(status().isNoContent());
    }

    private ResultActions expectRejected(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEM));
    }
}
