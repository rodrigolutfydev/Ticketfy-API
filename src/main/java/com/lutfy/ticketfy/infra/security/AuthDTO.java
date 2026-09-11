package com.lutfy.ticketfy.infra.security;

import java.util.UUID;

public record AuthDTO(
        String email,
        String password
) {
}
