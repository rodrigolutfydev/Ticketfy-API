package com.lutfy.ticketfy.infra.openapi;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.AuthOriginFilter;
import com.lutfy.ticketfy.infra.security.ClientAddressResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confere o contrato OpenAPI contra o comportamento real da API: o cadeado aparece só nas rotas que
 * o SecurityConfig fecha, e toda rota documenta as respostas de erro em ProblemDetail.
 */
@AutoConfigureMockMvc
class OpenApiContractTest extends IntegrationTestBase {

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "GET /actuator/health",
            "GET /events",
            "GET /events/categories",
            "GET /events/{id}",
            "GET /events/{id}/calendar.ics",
            "GET /events/{eventId}/ticket-types",
            "POST /users",
            "POST /auth/login",
            "POST /auth/google",
            "POST /auth/refresh",
            "POST /auth/logout",
            "POST /auth/password-reset/request",
            "POST /auth/password-reset/confirm");

    private static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

    @Autowired
    private MockMvc mockMvc;

    private final List<Route> routes = new ArrayList<>();

    @BeforeEach
    void loadDocument() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var paths = JsonMapper.builder().build().readTree(body).get("paths");
        paths.properties().forEach(path -> path.getValue().properties().forEach(operation ->
                routes.add(new Route(operation.getKey().toUpperCase(Locale.ROOT), path.getKey(), operation.getValue()))));
        assertThat(routes).hasSizeGreaterThan(50);
    }

    @Test
    void lockAppearsOnlyOnRoutesThatRequireLogin() throws Exception {
        var wrong = new TreeSet<String>();
        var open = new TreeSet<String>();
        for (var route : routes) {
            boolean requiresLogin = requiresLogin(route);
            if (!requiresLogin) {
                open.add(route.name());
            }
            if (route.locked() != requiresLogin) {
                wrong.add(route.name() + (requiresLogin ? " exige login e está sem cadeado"
                        : " é pública e aparece com cadeado"));
            }
        }
        assertThat(wrong).isEmpty();
        assertThat(open).containsExactlyInAnyOrderElementsOf(PUBLIC_ROUTES);
    }

    @Test
    void everyRouteDocumentsItsErrorsAsProblemDetail() {
        var wrong = new TreeSet<String>();
        for (var route : routes) {
            var errors = new ArrayList<String>();
            route.operation().get("responses").properties().forEach(response -> {
                if (Integer.parseInt(response.getKey()) >= 400) {
                    errors.add(response.getKey());
                    if (!route.path().startsWith("/actuator/") && !isProblemDetail(response.getValue())) {
                        wrong.add(route.name() + " " + response.getKey() + " não usa ProblemDetail");
                    }
                }
            });
            if (errors.isEmpty()) {
                wrong.add(route.name() + " não documenta respostas de erro");
            }
            if (route.locked() && !hasExample(route.operation(), "401", "authentication-required")) {
                wrong.add(route.name() + " não documenta 401 authentication-required");
            }
        }
        assertThat(wrong).isEmpty();
    }

    // Sem token, a rota fechada pelo SecurityConfig responde 401 authentication-required antes de
    // chegar ao controller; a pública chega ao controller e responde outra coisa.
    private boolean requiresLogin(Route route) throws Exception {
        var uri = route.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        var builder = request(HttpMethod.valueOf(route.method()), uri)
                .header("Origin", FRONTEND_ORIGIN)
                .header(AuthOriginFilter.HEADER, "1")
                .header(ClientAddressResolver.PROXY_SECRET_HEADER, PROXY_SECRET)
                .header(ClientAddressResolver.CLIENT_IP_HEADER, randomIp());
        if (Set.of("POST", "PUT", "PATCH").contains(route.method())) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        return response.getStatus() == 401
                && response.getContentAsString(StandardCharsets.UTF_8).contains("/authentication-required\"");
    }

    private static boolean isProblemDetail(JsonNode response) {
        var schema = response.path("content").path("application/problem+json").path("schema").path("$ref");
        return PROBLEM_SCHEMA.equals(schema.asString(""));
    }

    private static boolean hasExample(JsonNode operation, String status, String slug) {
        return operation.path("responses").path(status).path("content").path("application/problem+json")
                .path("examples").has(slug);
    }

    private static String randomIp() {
        var random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    private record Route(String method, String path, JsonNode operation) {

        String name() {
            return method + " " + path;
        }

        boolean locked() {
            var security = operation.path("security");
            return security.isArray() && !security.isEmpty();
        }
    }
}
