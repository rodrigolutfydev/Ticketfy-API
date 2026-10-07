package com.lutfy.ticketfy.payout;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutAccountIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Test
    void savesAccountAndReturnsOnlyMaskedData() throws Exception {
        var saved = saveAccount(organizer, "CPF", "529.982.247-25", "EMAIL", " Maria.Silva@Example.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("CPF"))
                .andExpect(jsonPath("$.document").value("***.982.247-**"))
                .andExpect(jsonPath("$.holderName").value("Maria da Silva"))
                .andExpect(jsonPath("$.pixKeyType").value("EMAIL"))
                .andExpect(jsonPath("$.pixKey").value("m***@example.com"))
                .andExpect(jsonPath("$.payoutsBlockedUntil").doesNotExist());
        var fetched = mockMvc.perform(get("/organizer/payout-account").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("***.982.247-**"))
                .andExpect(jsonPath("$.pixKey").value("m***@example.com"));

        for (var body : new String[]{
                saved.andReturn().getResponse().getContentAsString(),
                fetched.andReturn().getResponse().getContentAsString()}) {
            assertThat(body).doesNotContain(CPF, "maria.silva", "Senha");
        }
    }

    @Test
    void storesDocumentAndKeyEncrypted() throws Exception {
        registerAccount(organizer);

        var row = jdbc.queryForMap("SELECT document, pix_key FROM payout_accounts WHERE organizer_id = ?",
                organizer.getId());
        assertThat((String) row.get("document")).startsWith("v1:").doesNotContain(CPF);
        assertThat((String) row.get("pix_key")).startsWith("v1:").doesNotContain("maria.silva");
    }

    @Test
    void masksEveryKeyType() throws Exception {
        saveAccount(organizer, "CNPJ", "12.ABC.345/01DE-35", "CNPJ", "12ABC34501DE35", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("**.ABC.345/****-**"))
                .andExpect(jsonPath("$.pixKey").value("**.ABC.345/****-**"));
        saveAccount(organizer, "CNPJ", CNPJ, "PHONE", "+55 (11) 98765-4321", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("**.222.333/****-**"))
                .andExpect(jsonPath("$.pixKey").value("+55 (11) *****-4321"));
        saveAccount(organizer, "CPF", CPF, "RANDOM", "123E4567-E89B-12D3-A456-426614174000", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pixKey").value("********-****-****-****-********4000"));
        saveAccount(organizer, "CPF", CPF, "CPF", CPF, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pixKey").value("***.982.247-**"));
    }

    @Test
    void rejectsInvalidDocuments() throws Exception {
        saveAccount(organizer, "CPF", "52998224724", "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("document"));
        saveAccount(organizer, "CPF", "111.111.111-11", "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("document"));
        saveAccount(organizer, "CNPJ", "11222333000182", "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("document"));
        saveAccount(organizer, "CNPJ", "12ABC34501DE36", "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("document"));
        saveAccount(organizer, "CPF", CPF, "EMAIL", "not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pixKey"));
        saveAccount(organizer, "CPF", CPF, "PHONE", "11987654321", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pixKey"));
        mockMvc.perform(get("/organizer/payout-account").header("Authorization", bearer(organizer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-account-not-found"));
    }

    @Test
    void rejectsKeyOfAnotherHolder() throws Exception {
        saveAccount(organizer, "CPF", CPF, "CPF", OTHER_CPF, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "pix-key-holder-mismatch"));

        doReturn(Optional.of(OTHER_CPF)).when(payoutGateway).lookupPixKeyHolder(any());
        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "pix-key-holder-mismatch"));

        doReturn(Optional.empty()).when(payoutGateway).lookupPixKeyHolder(any());
        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "pix-key-not-found"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_accounts WHERE organizer_id = ?",
                Integer.class, organizer.getId())).isZero();
    }

    @Test
    void wrongPasswordIsForbiddenNotUnauthorized() throws Exception {
        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", "wrong-password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-password"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_accounts WHERE organizer_id = ?",
                Integer.class, organizer.getId())).isZero();
    }

    @Test
    void blocksPasswordGuessingAfterFiveWrongAttempts() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", "wrong-" + i)
                    .andExpect(status().isForbidden());
        }

        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "too-many-password-attempts"));
        requestPayout(organizer, "20.00", PASSWORD, null).andExpect(status().isTooManyRequests());

        travel(Duration.ofMinutes(15));
        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changingTheKeyBlocksPayoutsForTheCooldown() throws Exception {
        var start = Instant.parse("2026-11-01T12:00:00Z");
        travelTo(start);
        registerAccount(organizer);

        saveAccount(organizer, "CPF", CPF, "EMAIL", "maria.silva@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutsBlockedUntil").doesNotExist());

        travel(Duration.ofHours(1));
        saveAccount(organizer, "CPF", CPF, "CPF", CPF, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutsBlockedUntil").value(start.plus(Duration.ofHours(49)).toString()));

        travelTo(start.plus(Duration.ofHours(49)));
        mockMvc.perform(get("/organizer/payout-account").header("Authorization", bearer(organizer)))
                .andExpect(jsonPath("$.payoutsBlockedUntil").doesNotExist());
    }

    @Test
    void onlyOrganizersManagePayoutAccounts() throws Exception {
        saveAccount(buyer, "CPF", CPF, "EMAIL", "maria@example.com", PASSWORD).andExpect(status().isForbidden());
        mockMvc.perform(get("/organizer/payout-account").header("Authorization", bearer(user("ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/organizer/payout-account")).andExpect(status().isUnauthorized());
    }
}
