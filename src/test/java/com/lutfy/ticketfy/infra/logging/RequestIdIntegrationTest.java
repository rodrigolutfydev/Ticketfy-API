package com.lutfy.ticketfy.infra.logging;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.event.EventService;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestIdIntegrationTest extends IntegrationTestBase {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EventService eventService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unauthorizedResponseHasRequestId() throws Exception {
        var result = mockMvc.perform(get("/events/mine"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(requestIdOf(result)).matches(UUID_PATTERN);
    }

    @Test
    void notFoundResponseHasRequestId() throws Exception {
        var result = mockMvc.perform(get("/events/" + UUID.randomUUID() + "/ticket-types"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(requestIdOf(result)).matches(UUID_PATTERN);
    }

    @Test
    void tooManyRequestsResponseHasRequestId() throws Exception {
        for (int i = 0; i < 5; i++) {
            badLogin("10.0.42.1").andExpect(status().isUnauthorized());
        }

        var result = badLogin("10.0.42.1")
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(requestIdOf(result)).matches(UUID_PATTERN);
    }

    @Test
    void validClientRequestIdIsEchoed() throws Exception {
        var clientId = "Front-" + "a1".repeat(29);

        mockMvc.perform(get("/events/mine").header(RequestIdFilter.HEADER, clientId))
                .andExpect(header().string(RequestIdFilter.HEADER, clientId));
    }

    @Test
    void tooLongClientRequestIdIsReplaced() throws Exception {
        var tooLong = "a".repeat(65);

        var result = mockMvc.perform(get("/events/mine").header(RequestIdFilter.HEADER, tooLong)).andReturn();

        assertThat(requestIdOf(result)).matches(UUID_PATTERN);
    }

    @Test
    void clientRequestIdWithLineBreakIsReplaced() throws Exception {
        var result = mockMvc.perform(get("/events/mine").header(RequestIdFilter.HEADER, "abc\nforged-line"))
                .andReturn();

        assertThat(requestIdOf(result)).matches(UUID_PATTERN);
    }

    @Test
    void internalErrorBodyCarriesTheHeaderRequestId() throws Exception {
        when(eventService.findById(any())).thenThrow(new IllegalStateException("boom"));

        var result = mockMvc.perform(get("/events/" + UUID.randomUUID()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andReturn();

        var requestId = requestIdOf(result);
        assertThat(requestId).matches(UUID_PATTERN);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + requestId + "\"");
    }

    @Test
    void clientErrorBodyHasNoRequestId() throws Exception {
        mockMvc.perform(get("/events/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void mdcIsClearedAfterAuthenticatedRequest() throws Exception {
        var userId = insertUser("USER");
        MDC.clear();

        mockMvc.perform(get("/users/me").header("Authorization", bearer(userId)))
                .andExpect(status().isOk());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void mdcIsClearedAfterFailedRequest() throws Exception {
        when(eventService.findById(any())).thenThrow(new IllegalStateException("boom"));
        MDC.clear();

        mockMvc.perform(get("/events/" + UUID.randomUUID())).andExpect(status().isInternalServerError());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void accessLogHasMethodPathStatusAndDurationWithoutSecrets(CapturedOutput output) throws Exception {
        var userId = insertUser("USER");
        var token = bearer(userId);
        var path = "/events/" + UUID.randomUUID() + "/ticket-types";

        mockMvc.perform(get(path + "?secret=query-value")
                        .header("Authorization", token)
                        .header(RequestIdFilter.HEADER, "access-log-check"))
                .andExpect(status().isNotFound());

        var line = output.getOut().lines()
                .filter(l -> l.contains("access-log-check") && l.contains(RequestIdFilter.class.getSimpleName()))
                .findFirst()
                .orElseThrow();
        assertThat(line).containsPattern("GET " + path + " 404 \\d+ms");
        assertThat(output.getOut()).doesNotContain("query-value", token.substring("Bearer ".length()));
    }

    @Test
    void healthCheckHasRequestIdButNoAccessLog(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "health-check-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "health-check-id"));

        assertThat(output.getOut()).doesNotContain("/actuator/health");
    }

    private ResultActions badLogin(String address) throws Exception {
        return mockMvc.perform(post(URI.create("/login"))
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}"));
    }

    private String bearer(UUID userId) {
        return "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }

    private static String requestIdOf(MvcResult result) {
        return result.getResponse().getHeader(RequestIdFilter.HEADER);
    }
}
