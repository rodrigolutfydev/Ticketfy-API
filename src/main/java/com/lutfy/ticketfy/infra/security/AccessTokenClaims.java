package com.lutfy.ticketfy.infra.security;

import java.util.UUID;

public record AccessTokenClaims(UUID userId, UUID sessionId) {
}
