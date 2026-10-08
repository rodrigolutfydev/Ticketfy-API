package com.lutfy.ticketfy.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthSettings {

    private static final String HOST_PREFIX = "__Host-";
    private static final int MIN_PROXY_SECRET_LENGTH = 32;

    private final Duration accessTokenTtl;
    private final Duration refreshTokenIdleTtl;
    private final Duration sessionMaxAge;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String proxySecret;

    public AuthSettings(@Value("${ticketfy.auth.access-token-minutes}") long accessTokenMinutes,
                        @Value("${ticketfy.auth.refresh-token-idle-minutes}") long refreshTokenIdleMinutes,
                        @Value("${ticketfy.auth.session-max-hours}") long sessionMaxHours,
                        @Value("${ticketfy.auth.cookie-name}") String cookieName,
                        @Value("${ticketfy.auth.cookie-secure}") boolean cookieSecure,
                        @Value("${ticketfy.auth.proxy-secret}") String proxySecret,
                        Environment environment) {
        this.accessTokenTtl = positive(Duration.ofMinutes(accessTokenMinutes), "access-token-minutes");
        this.refreshTokenIdleTtl = positive(Duration.ofMinutes(refreshTokenIdleMinutes), "refresh-token-idle-minutes");
        this.sessionMaxAge = positive(Duration.ofHours(sessionMaxHours), "session-max-hours");
        this.cookieName = cookieName.trim();
        this.cookieSecure = cookieSecure;
        this.proxySecret = proxySecret.trim();

        if (this.cookieName.startsWith(HOST_PREFIX) && !cookieSecure) {
            throw new IllegalStateException("ticketfy.auth.cookie-name with the __Host- prefix requires ticketfy.auth.cookie-secure=true");
        }
        if (!this.proxySecret.isEmpty() && this.proxySecret.length() < MIN_PROXY_SECRET_LENGTH) {
            throw new IllegalStateException("PROXY_SHARED_SECRET must be at least 32 characters long");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            if (!cookieSecure) {
                throw new IllegalStateException("ticketfy.auth.cookie-secure must be true in the prod profile");
            }
            if (this.proxySecret.isEmpty()) {
                throw new IllegalStateException("PROXY_SHARED_SECRET is required in the prod profile");
            }
        }
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenIdleTtl() {
        return refreshTokenIdleTtl;
    }

    public Duration sessionMaxAge() {
        return sessionMaxAge;
    }

    public String cookieName() {
        return cookieName;
    }

    public boolean cookieSecure() {
        return cookieSecure;
    }

    public String proxySecret() {
        return proxySecret;
    }

    private static Duration positive(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalStateException("ticketfy.auth." + name + " must be positive");
        }
        return duration;
    }
}
