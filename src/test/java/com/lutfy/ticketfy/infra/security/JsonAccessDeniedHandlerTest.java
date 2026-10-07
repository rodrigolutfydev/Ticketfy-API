package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.infra.exception.ProblemDetailFactory;
import com.lutfy.ticketfy.infra.exception.ProblemDetailResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAccessDeniedHandlerTest {

    @Test
    void writesProblem403() throws Exception {
        var writer = new ProblemDetailResponseWriter(JsonMapper.builder().build(),
                new ProblemDetailFactory("https://ticketfy-api.onrender.com/problems"));
        var handler = new JsonAccessDeniedHandler(writer);
        var request = new MockHttpServletRequest("DELETE", "/events/123");
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("internal reason"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        var body = JsonMapper.builder().build().readTree(response.getContentAsString());
        assertThat(body.get("type").asString()).isEqualTo("https://ticketfy-api.onrender.com/problems/access-denied");
        assertThat(body.get("title").asString()).isEqualTo("Access denied");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asString()).isEqualTo("Access denied");
        assertThat(body.get("instance").asString()).isEqualTo("/events/123");
        assertThat(response.getContentAsString()).doesNotContain("internal reason");
    }
}
