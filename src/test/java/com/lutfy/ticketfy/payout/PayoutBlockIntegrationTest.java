package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutBlockIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private PayoutProcessingService processing;

    @Test
    void blockedOrganizerCannotRequestPayouts() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var admin = user("ADMIN");

        block(admin, organizer, " ").andExpect(status().isBadRequest());
        block(admin, organizer, "  Chargebacks em análise  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutsBlocked").value(true))
                .andExpect(jsonPath("$.blockReason").value("Chargebacks em análise"))
                .andExpect(jsonPath("$.blockedBy").value(admin.getId().toString()));
        block(admin, organizer, "de novo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-payout-block-state"));

        requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payouts-blocked"));
        assertThat(balance(organizer).get("payoutsBlocked").asBoolean()).isTrue();
        mockMvc.perform(get("/admin/organizers").param("email", organizer.getEmail()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizer.getId().toString()))
                .andExpect(jsonPath("$.payoutsBlocked").value(true));

        unblock(admin, organizer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutsBlocked").value(false));
        unblock(admin, organizer).andExpect(status().isConflict());
        assertThat(balance(organizer).get("payoutsBlocked").asBoolean()).isFalse();
        requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void requestedPayoutIsNotProcessedWhileBlocked() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var admin = user("ADMIN");
        var payoutId = body(requestPayout(organizer, "30.00", PASSWORD, null)).get("id").asString();
        block(admin, organizer, "Revisão cadastral").andExpect(status().isOk());

        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("REQUESTED");

        unblock(admin, organizer).andExpect(status().isOk());
        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
    }

    @Test
    void processingPayoutFinishesEvenWhenBlocked() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var admin = user("ADMIN");
        var payoutId = body(requestPayout(organizer, "30.00", PASSWORD, null)).get("id").asString();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("timeout after transfer");
        }).when(payoutGateway).transfer(any());
        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PROCESSING");

        block(admin, organizer, "Revisão cadastral").andExpect(status().isOk());
        doCallRealMethod().when(payoutGateway).transfer(any());
        travel(Duration.ofMinutes(11));
        processing.processAll();

        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
    }

    @Test
    void blockDoesNotAffectSalesLedgerOrCancellation() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var admin = user("ADMIN");
        var payoutId = body(requestPayout(organizer, "30.00", PASSWORD, null)).get("id").asString();
        block(admin, organizer, "Revisão cadastral").andExpect(status().isOk());

        mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
        travelTo(null);
        releasedSales(organizer, "50.00");

        var balance = balance(organizer);
        assertThat(balance.get("available").decimalValue()).isEqualByComparingTo("142.50");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE organizer_id = ? AND type = 'SALE_CREDIT'",
                Integer.class, organizer.getId())).isEqualTo(2);
    }

    @Test
    void onlyOrganizersCanBeBlocked() throws Exception {
        var admin = user("ADMIN");

        block(admin, buyer, "não é organizador")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "organizer-not-found"));
        mockMvc.perform(get("/admin/organizers").param("email", buyer.getEmail()).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    private ResultActions block(User admin, User target, String reason) throws Exception {
        return mockMvc.perform(post("/admin/organizers/" + target.getId() + "/payout-block")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions unblock(User admin, User target) throws Exception {
        return mockMvc.perform(delete("/admin/organizers/" + target.getId() + "/payout-block")
                .header("Authorization", bearer(admin)));
    }

    private String payoutStatus(String payoutId) {
        return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?::uuid", String.class, payoutId);
    }
}
