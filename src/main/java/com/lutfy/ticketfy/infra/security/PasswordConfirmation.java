package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.TooManyPasswordAttemptsException;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordConfirmation {

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final Map<UUID, Failures> failures = new ConcurrentHashMap<>();

    public PasswordConfirmation(PasswordEncoder passwordEncoder, Clock clock,
                                @Value("${ticketfy.password-confirmation.max-attempts}") int maxAttempts,
                                @Value("${ticketfy.password-confirmation.window-seconds}") long windowSeconds) {
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void verify(User user, String rawPassword) {
        var now = clock.instant();
        var current = failures.get(user.getId());
        if (current != null && current.expired(now, window)) {
            failures.remove(user.getId(), current);
            current = null;
        }
        if (current != null && current.count() >= maxAttempts) {
            long retryAfter = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
            throw new TooManyPasswordAttemptsException("Too many wrong passwords. Try again later.", retryAfter);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            failures.merge(user.getId(), new Failures(now, 1), (old, added) ->
                    old.expired(now, window) ? added : new Failures(old.start(), old.count() + 1));
            throw new ProblemException(ProblemType.INVALID_PASSWORD, "Invalid password");
        }
        failures.remove(user.getId());
    }

    @Scheduled(fixedDelay = 600000)
    public void removeExpired() {
        var now = clock.instant();
        failures.values().removeIf(entry -> entry.expired(now, window));
    }

    private record Failures(Instant start, int count) {
        boolean expired(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
