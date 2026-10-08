package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.auth.IssuedSession;

public record LoginResponseDTO(String token, long expiresIn) {

    public LoginResponseDTO(IssuedSession session) {
        this(session.accessToken(), session.accessTokenTtl().toSeconds());
    }

    @Override
    public String toString() {
        return "LoginResponseDTO[expiresIn=" + expiresIn + "]";
    }
}
