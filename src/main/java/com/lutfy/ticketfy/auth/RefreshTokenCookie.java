package com.lutfy.ticketfy.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class RefreshTokenCookie {

    private final AuthSettings settings;

    public RefreshTokenCookie(AuthSettings settings) {
        this.settings = settings;
    }

    public String issue(IssuedSession session) {
        return build(session.refreshToken(), session.refreshTokenTtl());
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    public Optional<String> read(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> settings.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
