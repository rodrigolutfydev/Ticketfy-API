package com.lutfy.ticketfy.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveUserDTOTest {

    @Test
    void registrationToStringNeverShowsThePasswordOrPersonalData() {
        var dto = new UserRegistrationDTO("Ana Souza", "ana@email.com", "senha-secreta-123");

        assertThat(dto.toString())
                .doesNotContain("senha-secreta-123")
                .doesNotContain("ana@email.com")
                .doesNotContain("Ana Souza");
    }

    @Test
    void passwordChangeToStringNeverShowsEitherPassword() {
        var dto = new PasswordChangeDTO("senha-atual-123", "senha-nova-456");

        assertThat(dto.toString()).doesNotContain("senha-atual-123").doesNotContain("senha-nova-456");
    }
}
