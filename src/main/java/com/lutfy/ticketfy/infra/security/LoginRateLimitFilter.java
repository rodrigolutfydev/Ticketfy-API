package com.lutfy.ticketfy.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${ticketfy.login.max-attempts}") int maxAttempts,
                                @Value("${ticketfy.login.window-seconds}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        var window = attempts.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || now - current.start() >= windowMillis
                        ? new Window(now, new AtomicInteger())
                        : current);

        if (window.count().incrementAndGet() > maxAttempts) {
            long retryAfterSeconds = Math.max(1, (window.start() + windowMillis - now) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many login attempts. Try again later.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    @Scheduled(fixedDelay = 600000)
    public void removeExpiredWindows() {
        long now = System.currentTimeMillis();
        attempts.values().removeIf(window -> now - window.start() >= windowMillis);
    }

    private record Window(long start, AtomicInteger count) {}
}
