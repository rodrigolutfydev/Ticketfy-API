package com.lutfy.ticketfy.auth.passwordreset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PasswordResetSettings {

    private final Duration tokenTtl;
    private final int maxRequestsPerIp;
    private final int maxRequestsPerEmail;
    private final int maxConfirmationsPerIp;
    private final Duration window;

    public PasswordResetSettings(@Value("${ticketfy.password-reset.token-minutes:30}") long tokenMinutes,
                                 @Value("${ticketfy.password-reset.max-requests-per-ip:10}") int maxRequestsPerIp,
                                 @Value("${ticketfy.password-reset.max-requests-per-email:3}") int maxRequestsPerEmail,
                                 @Value("${ticketfy.password-reset.max-confirmations-per-ip:10}") int maxConfirmationsPerIp,
                                 @Value("${ticketfy.password-reset.window-seconds:900}") long windowSeconds) {
        this.tokenTtl = Duration.ofMinutes(tokenMinutes);
        this.maxRequestsPerIp = maxRequestsPerIp;
        this.maxRequestsPerEmail = maxRequestsPerEmail;
        this.maxConfirmationsPerIp = maxConfirmationsPerIp;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }

    public int maxRequestsPerIp() {
        return maxRequestsPerIp;
    }

    public int maxRequestsPerEmail() {
        return maxRequestsPerEmail;
    }

    public int maxConfirmationsPerIp() {
        return maxConfirmationsPerIp;
    }

    public Duration window() {
        return window;
    }
}
