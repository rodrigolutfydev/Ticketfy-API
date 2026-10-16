package com.lutfy.ticketfy.auth.passwordreset;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Janelas fixas em memória, como no login. Toda tentativa conta, exista a
// conta ou não, para o 429 não revelar quem tem cadastro.
@Component
public class PasswordResetLimiter {

    private final Clock clock;
    private final PasswordResetSettings settings;
    private final Map<String, Window> requestsByIp = new ConcurrentHashMap<>();
    private final Map<String, Window> requestsByEmail = new ConcurrentHashMap<>();
    private final Map<String, Window> confirmationsByIp = new ConcurrentHashMap<>();

    public PasswordResetLimiter(Clock clock, PasswordResetSettings settings) {
        this.clock = clock;
        this.settings = settings;
    }

    public void acquireRequest(String ip, String email) {
        var now = clock.instant();
        var byIp = count(requestsByIp, ip, now);
        var byEmail = count(requestsByEmail, email.trim().toLowerCase(Locale.ROOT), now);
        if (byIp.count() > settings.maxRequestsPerIp()) {
            throw tooMany(byIp, now);
        }
        if (byEmail.count() > settings.maxRequestsPerEmail()) {
            throw tooMany(byEmail, now);
        }
    }

    public void acquireConfirmation(String ip) {
        var now = clock.instant();
        var byIp = count(confirmationsByIp, ip, now);
        if (byIp.count() > settings.maxConfirmationsPerIp()) {
            throw tooMany(byIp, now);
        }
    }

    @Scheduled(fixedDelay = 600000)
    public void removeExpired() {
        var now = clock.instant();
        for (var windows : java.util.List.of(requestsByIp, requestsByEmail, confirmationsByIp)) {
            windows.values().removeIf(window -> window.expired(now, settings.window()));
        }
    }

    private Window count(Map<String, Window> windows, String key, Instant now) {
        return windows.compute(key, (k, current) -> current == null || current.expired(now, settings.window())
                ? new Window(now, 1)
                : new Window(current.start(), current.count() + 1));
    }

    private TooManyRequestsException tooMany(Window window, Instant now) {
        long retryAfter = Math.max(1, Duration.between(now, window.start().plus(settings.window())).toSeconds());
        return new TooManyRequestsException(ProblemType.TOO_MANY_PASSWORD_RESET_ATTEMPTS,
                "Too many password reset attempts. Try again later.", retryAfter);
    }

    private record Window(Instant start, int count) {
        boolean expired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
