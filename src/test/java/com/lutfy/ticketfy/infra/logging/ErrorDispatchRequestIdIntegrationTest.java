package com.lutfy.ticketfy.infra.logging;

import com.lutfy.ticketfy.IntegrationTestBase;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchRequestIdIntegrationTest extends IntegrationTestBase {

    private static final String EXPLODING_PATH = "/test-only/exploding-filter";

    @LocalServerPort
    private int port;

    @Test
    void exceptionThrownByFilterReturnsProblemWithHeaderRequestId() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + EXPLODING_PATH))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(MediaType.parseMediaType(type).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        var requestId = response.headers().firstValue(RequestIdFilter.HEADER).orElseThrow();
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(500);
        assertThat(body.path("requestId").asString()).isEqualTo(requestId);
    }

    @TestConfiguration
    static class ExplodingFilterConfig {

        @Bean
        FilterRegistrationBean<Filter> explodingFilter() {
            Filter filter = (request, response, chain) -> {
                throw new IllegalStateException("filter failure");
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns(EXPLODING_PATH);
            registration.setOrder(-101);
            return registration;
        }
    }
}
