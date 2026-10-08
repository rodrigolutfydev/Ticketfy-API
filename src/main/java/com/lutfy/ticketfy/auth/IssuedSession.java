package com.lutfy.ticketfy.auth;

import java.time.Duration;

public record IssuedSession(String accessToken, Duration accessTokenTtl, String refreshToken, Duration refreshTokenTtl) {

    @Override
    public String toString() {
        return "IssuedSession[accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
