package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.exception.TooManyCouponAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CouponAttemptLimiter {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<UUID, Failures> failures = new ConcurrentHashMap<>();

    public CouponAttemptLimiter(Clock clock,
                                @Value("${ticketfy.coupon.max-failed-attempts}") int maxFailures,
                                @Value("${ticketfy.coupon.window-seconds}") long windowSeconds) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void checkAllowed(UUID userId) {
        var now = clock.instant();
        var current = failures.get(userId);
        if (current != null && current.expired(now, window)) {
            failures.remove(userId, current);
            return;
        }
        if (current != null && current.count() >= maxFailures) {
            long retryAfter = Math.max(1, Duration.between(now, current.start().plus(window)).toSeconds());
            throw new TooManyCouponAttemptsException("Too many invalid coupon attempts. Try again later.", retryAfter);
        }
    }

    public void recordFailure(UUID userId) {
        var now = clock.instant();
        failures.merge(userId, new Failures(now, 1), (old, added) ->
                old.expired(now, window) ? added : new Failures(old.start(), old.count() + 1));
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
