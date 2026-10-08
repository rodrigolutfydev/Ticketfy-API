package com.lutfy.ticketfy;

import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.infra.security.AuthOriginFilter;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest
public abstract class IntegrationTestBase {

    protected static final String PROXY_SECRET = "test-proxy-secret-with-at-least-32-chars";
    protected static final String FRONTEND_ORIGIN = "http://localhost:5173";

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("api.security.token.secret", () -> "test-secret-with-at-least-32-characters");
        registry.add("ticketfy.payout.job.enabled", () -> "false");
        registry.add("ticketfy.auth.cleanup.enabled", () -> "false");
        registry.add("ticketfy.auth.cookie-name", () -> "__Host-ticketfy_rt");
        registry.add("ticketfy.auth.cookie-secure", () -> "true");
        registry.add("ticketfy.auth.proxy-secret", () -> PROXY_SECRET);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected AuthSessionService authSessions;

    protected String accessToken(User user) {
        return authSessions.start(user).accessToken();
    }

    protected static RequestPostProcessor fromFrontend() {
        return request -> {
            request.addHeader("Origin", FRONTEND_ORIGIN);
            request.addHeader(AuthOriginFilter.HEADER, "1");
            return request;
        };
    }

    protected UUID insertUser(String role) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
                id, "Test User", id + "@test.com", "not-used", role);
        return id;
    }

    protected UUID insertEvent(UUID organizerId) {
        var id = UUID.randomUUID();
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        jdbc.update("""
                INSERT INTO events (id, name, description, venue_name, address, city, state,
                                    starts_at, ends_at, organizer_id, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, NOW())
                """,
                id, "Test Event", "Event for tests", "Test Arena", "Rua A, 100", "Rio de Janeiro", "RJ",
                Timestamp.from(startsAt), Timestamp.from(startsAt.plus(Duration.ofHours(3))), organizerId);
        return id;
    }

    protected UUID insertTicketType(UUID eventId, int quantityTotal) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price,
                                          quantity_total, quantity_sold, max_per_order, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, true, NOW())
                """,
                id, eventId, "VIP", "Test ticket type", new BigDecimal("80.00"), quantityTotal, 4);
        return id;
    }
}