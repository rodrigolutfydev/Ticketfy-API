package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService authSessions;

    public SecurityFilter(AuthSessionService authSessions) {
        this.authSessions = authSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        recoverToken(request)
                .flatMap(authSessions::authenticate)
                .ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private Optional<String> recoverToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("Authorization"))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()).trim());
    }

    private void authenticate(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put("userId", user.getId().toString());
    }
}