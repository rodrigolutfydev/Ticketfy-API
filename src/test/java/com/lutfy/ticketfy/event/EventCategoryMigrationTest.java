package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCategoryMigrationTest extends IntegrationTestBase {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private String database;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate legacy;

    @BeforeEach
    void createDatabaseAtV23() {
        database = "category_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        dataSource = new DriverManagerDataSource(url.replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1"),
                username, password);
        flyway("23").migrate();
        legacy = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropDatabase() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = ?",
                Integer.class, database)).isZero();
        jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }

    @Test
    void existingEventsBecomeOther() {
        var organizer = UUID.randomUUID();
        legacy.update("INSERT INTO users (id, name, email, password, role) VALUES (?, 'Org', ?, 'x', 'ORGANIZER')",
                organizer, organizer + "@legacy.com");
        var upcoming = legacyEvent(organizer, Instant.now().plus(Duration.ofDays(10)), true);
        var past = legacyEvent(organizer, Instant.now().minus(Duration.ofDays(10)), true);
        var inactive = legacyEvent(organizer, Instant.now().plus(Duration.ofDays(5)), false);

        flyway(null).migrate();

        for (var eventId : new UUID[] {upcoming, past, inactive}) {
            assertThat(legacy.queryForObject("SELECT category FROM events WHERE id = ?", String.class, eventId))
                    .isEqualTo("OTHER");
        }
        assertThatThrownBy(() -> legacy.update("UPDATE events SET category = 'CONCERTS' WHERE id = ?", upcoming))
                .hasMessageContaining("ck_events_category");
    }

    private UUID legacyEvent(UUID organizer, Instant startsAt, boolean active) {
        var id = UUID.randomUUID();
        legacy.update("""
                INSERT INTO events (id, name, venue_name, address, city, state, starts_at, organizer_id, active)
                VALUES (?, 'Legacy', 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ', ?, ?, ?)
                """, id, Timestamp.from(startsAt), organizer, active);
        return id;
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
