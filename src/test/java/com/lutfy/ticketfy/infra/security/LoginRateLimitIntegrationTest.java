package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.IntegrationTestBase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest extends IntegrationTestBase {

    private static final String BAD_LOGIN = "{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}";
    private static final String PROXY_ADDRESS = "172.64.0.10";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksAfterMaxAttemptsPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("/auth/login", "10.0.9.1").andExpect(status().isUnauthorized());
        }

        login("/auth/login", "10.0.9.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ticketfy-api.onrender.com/problems/too-many-login-attempts"))
                .andExpect(jsonPath("$.title").value("Too many login attempts"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Too many login attempts. Try again later."))
                .andExpect(jsonPath("$.instance").value("/auth/login"));

        login("/auth/login", "10.0.9.2").andExpect(status().isUnauthorized());
    }

    @Test
    void encodedLoginPathCountsTowardsTheSameLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("/auth/%6Cogin", "10.0.9.3").andExpect(status().isUnauthorized());
        }

        login("/auth/login", "10.0.9.3").andExpect(status().isTooManyRequests());
    }

    @Test
    void trustedProxyLimitsEachClientAddressSeparately() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(viaProxy("198.51.100.1", PROXY_SECRET)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(viaProxy("198.51.100.1", PROXY_SECRET)).andExpect(status().isTooManyRequests());
        mockMvc.perform(viaProxy("198.51.100.2", PROXY_SECRET)).andExpect(status().isUnauthorized());
    }

    @Test
    void clientAddressWithoutTheProxySecretIsIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(viaProxy("198.51.100." + (10 + i), null)
                            .with(request -> {
                                request.setRemoteAddr("10.0.9.4");
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(viaProxy("198.51.100.20", "wrong-secret-with-at-least-32-characters")
                        .with(request -> {
                            request.setRemoteAddr("10.0.9.4");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void malformedClientAddressFallsBackToTheRemoteAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(viaProxy("not-an-ip-" + i, PROXY_SECRET)
                            .with(request -> {
                                request.setRemoteAddr("10.0.9.5");
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(viaProxy("not-an-ip-x", PROXY_SECRET)
                        .with(request -> {
                            request.setRemoteAddr("10.0.9.5");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder viaProxy(String clientAddress, String secret) {
        var builder = post("/auth/login")
                .with(fromFrontend())
                .with(request -> {
                    request.setRemoteAddr(PROXY_ADDRESS);
                    return request;
                })
                .header(ClientAddressResolver.CLIENT_IP_HEADER, clientAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BAD_LOGIN);
        return secret == null ? builder : builder.header(ClientAddressResolver.PROXY_SECRET_HEADER, secret);
    }

    private ResultActions login(String path, String address) throws Exception {
        return mockMvc.perform(post(URI.create(path))
                .with(fromFrontend())
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(BAD_LOGIN));
    }
}
