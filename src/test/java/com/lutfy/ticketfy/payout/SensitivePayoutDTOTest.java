package com.lutfy.ticketfy.payout;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SensitivePayoutDTOTest {

    @Test
    void payoutRequestToStringNeverShowsThePassword() {
        var dto = new PayoutRequestDTO(new BigDecimal("50.00"), "senha-secreta-123");

        assertThat(dto.toString()).doesNotContain("senha-secreta-123").contains("50.00");
    }

    @Test
    void payoutAccountUpdateToStringNeverShowsThePasswordOrBankingData() {
        var dto = new PayoutAccountUpdateDTO(DocumentType.CPF, "52998224725", "Maria da Silva",
                PixKeyType.EMAIL, "maria@email.com", "senha-secreta-123");

        assertThat(dto.toString())
                .doesNotContain("senha-secreta-123")
                .doesNotContain("52998224725")
                .doesNotContain("maria@email.com")
                .doesNotContain("Maria da Silva");
    }
}
