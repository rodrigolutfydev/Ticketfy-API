package com.lutfy.ticketfy.auth.passwordreset;

import java.util.UUID;

/**
 * Carrega o token em claro só em memória, até o e-mail ser montado depois do
 * commit. Nunca é gravado nem registrado no log.
 */
public record PasswordResetRequestedEvent(UUID userId, String rawToken) {
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[userId=" + userId + "]";
    }
}
