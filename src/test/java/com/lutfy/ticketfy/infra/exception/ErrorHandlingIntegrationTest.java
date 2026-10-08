package com.lutfy.ticketfy.infra.exception;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest extends IntegrationTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;


    @Test
    void malformedJsonReturnsGenericBadRequest() throws Exception {
        var body = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "malformed-request-body"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("JSON parse").doesNotContain("line:", "jackson", "Exception");
    }

    @Test
    void missingBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/users/me/avatar")
                        .header("Authorization", tokenFor(insertUser("USER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void wrongFieldTypeReturnsBadRequest() throws Exception {
        var body = mockMvc.perform(post("/orders")
                        .header("Authorization", tokenFor(insertUser("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ticketTypeId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"abc\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Integer", "abc");
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Ana"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Unsupported media type"));
    }

    @Test
    void loginRespondsTheSameForUnknownEmailAndWrongPassword() throws Exception {
        var email = "login." + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senhaCorreta123\"}"))
                .andExpect(status().isCreated());

        var wrongPassword = mockMvc.perform(post("/auth/login").with(fromFrontend()).with(remoteAddr("10.0.7.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"senhaErrada123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        var unknownEmail = mockMvc.perform(post("/auth/login").with(fromFrontend()).with(remoteAddr("10.0.7.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody." + UUID.randomUUID() + "@mail.com\",\"password\":\"senhaErrada123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword).contains("\"detail\":\"Invalid credentials\"")
                .contains("\"type\":\"" + PROBLEMS + "invalid-credentials\"");

        mockMvc.perform(post("/auth/login").with(fromFrontend()).with(remoteAddr("10.0.7.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"senhaCorreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void unauthenticatedRequestReturnsProblem401() throws Exception {
        expectProblem(mockMvc.perform(get("/orders").accept(MediaType.APPLICATION_JSON)),
                401, "authentication-required", "Authentication required", "/orders");
    }

    @Test
    void forbiddenRoleReturnsProblem403() throws Exception {
        var path = "/events/" + UUID.randomUUID() + "/cancel";
        expectProblem(mockMvc.perform(post(path)
                        .header("Authorization", tokenFor(insertUser("USER")))
                        .accept(MediaType.APPLICATION_JSON)),
                403, "access-denied", "Access denied", path);
    }

    @Test
    void validationErrorsListEachField() throws Exception {
        expectProblem(mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}")),
                400, "validation-failed", "Validation failed", "/users")
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email", "password")))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value("Password must be at least 8 characters long"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void unknownRouteReturnsProblem404() throws Exception {
        expectProblem(mockMvc.perform(get("/does-not-exist")
                        .header("Authorization", tokenFor(insertUser("USER")))),
                404, "resource-not-found", "Resource not found", "/does-not-exist");
    }

    @Test
    void wrongMethodReturnsProblem405WithAllowHeader() throws Exception {
        expectProblem(mockMvc.perform(delete("/users/me/avatar")
                        .header("Authorization", tokenFor(insertUser("USER")))),
                405, "method-not-allowed", "Method not allowed", "/users/me/avatar")
                .andExpect(header().string("Allow", containsString("PATCH")));
    }

    @Test
    void errorDispatchReturnsGenericProblemWithoutInternals() throws Exception {
        var body = mockMvc.perform(get("/error").with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/events");
                            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("connection refused to db-host:5432"));
                            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "connection refused to db-host:5432");
                            return request;
                        }))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "internal-error"))
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("db-host", "IllegalStateException", "timestamp", "trace");
    }

    private ResultActions expectProblem(ResultActions actions, int status, String slug, String detail, String instance)
            throws Exception {
        return actions
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(PROBLEMS + slug))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance));
    }

    private static RequestPostProcessor remoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + accessToken(userRepository.findById(userId).orElseThrow());
    }
}
