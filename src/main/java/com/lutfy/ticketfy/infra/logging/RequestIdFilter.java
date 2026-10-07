package com.lutfy.ticketfy.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAsyncDispatch(request) || request.getAttribute(ATTRIBUTE) instanceof String) {
            continueExisting(request, response, chain);
            return;
        }

        var requestId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            try {
                logAccess(request, response, failed, start);
            } finally {
                MDC.clear();
            }
        }
    }

    private void continueExisting(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var requestId = request.getAttribute(ATTRIBUTE) instanceof String id ? id : resolve(null);
        if (!response.containsHeader(HEADER)) {
            response.setHeader(HEADER, requestId);
        }
        var previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.clear();
            }
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, boolean failed, long start) {
        var path = request.getRequestURI();
        if (HEALTH_PATH.equals(path)) {
            return;
        }
        int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("{} {} {} {}ms", request.getMethod(), path, status, durationMs);
    }

    static String resolve(String candidate) {
        return candidate != null && SAFE_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
