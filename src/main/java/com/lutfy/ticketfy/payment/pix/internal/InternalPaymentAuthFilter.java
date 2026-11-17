package com.lutfy.ticketfy.payment.pix.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalPaymentAuthFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalPaymentAuthFilter(
            @Value("${ticketfy.payments.internal-token}")
            String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "Token interno de pagamentos não configurado"
            );
        }

        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(
                "POST".equals(request.getMethod())
                && "/internal/payments/confirm".equals(
                        request.getServletPath()
                )
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        String expected = "Bearer " + internalToken;

        boolean valid = authorization != null
                && MessageDigest.isEqual(
                        authorization.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8)
                );

        if (!valid) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid internal payment token"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}