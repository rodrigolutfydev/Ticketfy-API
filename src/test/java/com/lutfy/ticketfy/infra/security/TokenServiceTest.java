package com.lutfy.ticketfy.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.lutfy.ticketfy.auth.AuthSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-07T12:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    @Test
    void acceptsTheTokenUntilTenMinutesAfterIssue() {
        var token = serviceAt(ISSUED_AT).generateToken(USER, SESSION, ISSUED_AT);

        assertThat(serviceAt(ISSUED_AT.plus(Duration.ofMinutes(9))).validateToken(token))
                .contains(new AccessTokenClaims(USER, SESSION));
        assertThat(serviceAt(ISSUED_AT.plus(Duration.ofMinutes(10)).plusSeconds(1)).validateToken(token)).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        var token = JWT.create().withIssuer("API Ticketfy").withSubject(USER.toString())
                .withClaim("sid", SESSION.toString())
                .withExpiresAt(ISSUED_AT.plusSeconds(60))
                .sign(Algorithm.HMAC256("another-secret-with-at-least-32-characters"));

        assertThat(serviceAt(ISSUED_AT).validateToken(token)).isEmpty();
    }

    @Test
    void rejectsTokensWithoutSessionOrWithMalformedIds() {
        var algorithm = Algorithm.HMAC256(SECRET);
        var withoutSession = JWT.create().withIssuer("API Ticketfy").withSubject(USER.toString())
                .withExpiresAt(ISSUED_AT.plusSeconds(60)).sign(algorithm);
        var emailSubject = JWT.create().withIssuer("API Ticketfy").withSubject("ana@mail.com")
                .withClaim("sid", SESSION.toString()).withExpiresAt(ISSUED_AT.plusSeconds(60)).sign(algorithm);
        var wrongIssuer = JWT.create().withIssuer("Someone else").withSubject(USER.toString())
                .withClaim("sid", SESSION.toString()).withExpiresAt(ISSUED_AT.plusSeconds(60)).sign(algorithm);

        var service = serviceAt(ISSUED_AT);
        assertThat(service.validateToken(withoutSession)).isEmpty();
        assertThat(service.validateToken(emailSubject)).isEmpty();
        assertThat(service.validateToken(wrongIssuer)).isEmpty();
        assertThat(service.validateToken("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesAShortSecret() {
        assertThatThrownBy(() -> new TokenService("short", settings(), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    private static TokenService serviceAt(Instant now) {
        return new TokenService(SECRET, settings(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static AuthSettings settings() {
        return new AuthSettings(10, 30, 12, "ticketfy_rt", false, "", new MockEnvironment());
    }
}
