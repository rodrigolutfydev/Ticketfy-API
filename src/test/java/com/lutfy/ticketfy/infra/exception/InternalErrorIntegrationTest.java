package com.lutfy.ticketfy.infra.exception;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InternalErrorIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void unexpectedExceptionNeverLeaksInternals() throws Exception {
        when(eventService.findById(any())).thenThrow(
                new IllegalStateException("SELECT password FROM users WHERE email = 'admin@ticketfy.com'"));
        var path = "/events/" + UUID.randomUUID();

        var body = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ticketfy-api.onrender.com/problems/internal-error"))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(jsonPath("$.instance").value(path))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SELECT", "password", "IllegalStateException", "java.", "at com.", "trace");
    }
}
