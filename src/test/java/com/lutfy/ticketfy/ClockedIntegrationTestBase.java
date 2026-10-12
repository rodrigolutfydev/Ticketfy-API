package com.lutfy.ticketfy;

import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.when;

@AutoConfigureMockMvc
public abstract class ClockedIntegrationTestBase extends IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @MockitoBean
    protected Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void setUpClock() {
        now.set(null);
        when(clock.instant()).thenAnswer(invocation -> {
            var fixed = now.get();
            return fixed == null ? Instant.now() : fixed;
        });
    }

    protected void travelTo(Instant instant) {
        now.set(instant);
    }

    protected static Instant futureStart() {
        return Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    protected User user(String role) {
        return userRepository.findById(insertUser(role)).orElseThrow();
    }

    protected String bearer(User user) {
        return "Bearer " + accessToken(user);
    }

    protected UUID event(UUID organizerId, String name, Instant startsAt, Instant endsAt, boolean featured) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, name, venue_name, address, city, state, starts_at, ends_at,
                                    organizer_id, active, featured)
                VALUES (?, ?, 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ', ?, ?, ?, true, ?)
                """, id, name, Timestamp.from(startsAt), endsAt == null ? null : Timestamp.from(endsAt),
                organizerId, featured);
        return id;
    }

    protected UUID lot(UUID eventId, String price, int quantity) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold,
                                          max_per_order, active)
                VALUES (?, ?, ?, ?, ?, 0, 10, true)
                """, id, eventId, "Lote " + id, new BigDecimal(price), quantity);
        return id;
    }
}
