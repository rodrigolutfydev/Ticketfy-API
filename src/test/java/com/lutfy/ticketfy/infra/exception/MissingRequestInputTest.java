package com.lutfy.ticketfy.infra.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissingRequestInputTest {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(new ProblemDetailFactory(PROBLEMS)))
                .build();
    }

    @Test
    void missingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/probe/param").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "missing-parameter"))
                .andExpect(jsonPath("$.title").value("Missing parameter"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Required parameter 'page' is missing"))
                .andExpect(jsonPath("$.instance").value("/probe/param"));
    }

    @Test
    void missingRequiredHeaderReturns400() throws Exception {
        mockMvc.perform(get("/probe/header").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "missing-header"))
                .andExpect(jsonPath("$.title").value("Missing header"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is missing"))
                .andExpect(jsonPath("$.instance").value("/probe/header"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/param")
        String param(@RequestParam int page) {
            return "ok";
        }

        @GetMapping("/probe/header")
        String header(@RequestHeader("Idempotency-Key") String key) {
            return "ok";
        }
    }
}
