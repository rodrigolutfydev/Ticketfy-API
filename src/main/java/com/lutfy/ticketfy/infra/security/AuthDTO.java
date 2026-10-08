package com.lutfy.ticketfy.infra.security;

public record AuthDTO(
        String email,
        String password
) {
    @Override
    public String toString() {
        return "AuthDTO[]";
    }
}
