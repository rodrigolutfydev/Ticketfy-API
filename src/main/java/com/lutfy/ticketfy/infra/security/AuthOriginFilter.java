package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.infra.exception.ProblemDetailResponseWriter;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthOriginFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Ticketfy-Auth";

    private static final RequestMatcher AUTH = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/**");

    private final Set<String> allowedOrigins;
    private final ProblemDetailResponseWriter writer;

    public AuthOriginFilter(@Value("${ticketfy.cors.allowed-origins}") List<String> allowedOrigins,
                            ProblemDetailResponseWriter writer) {
        this.allowedOrigins = Set.copyOf(allowedOrigins.stream().map(String::trim).toList());
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTH.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin) || !"1".equals(request.getHeader(HEADER))) {
            writer.write(request, response, ProblemType.AUTH_REQUEST_REJECTED, "Request rejected");
            return;
        }
        chain.doFilter(request, response);
    }
}
