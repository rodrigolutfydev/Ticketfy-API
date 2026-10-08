package com.lutfy.ticketfy.infra.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDTOTest {

    @Test
    void toStringNeverShowsTheCredentials() {
        var dto = new AuthDTO("ana@email.com", "senha-secreta-123");

        assertThat(dto.toString()).doesNotContain("senha-secreta-123").doesNotContain("ana@email.com");
    }
}
