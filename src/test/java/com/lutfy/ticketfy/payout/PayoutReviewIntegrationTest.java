package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutReviewIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private PayoutProcessingService processing;

    @Autowired
    private AdminPayoutService adminService;

    @Autowired
    private PayoutRequestService requestService;

    @Test
    void firstPayoutGoesToReviewAndIsNotProcessed() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var admin = user("ADMIN");

        var payoutId = body(requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))).get("id").asString();
        processing.processAll();

        assertThat(payoutStatus(payoutId)).isEqualTo("UNDER_REVIEW");
        assertThat(balance(organizer).get("inPayout").decimalValue()).isEqualByComparingTo("20.00");
        var queue = body(mockMvc.perform(get("/admin/payouts").param("size", "100").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        var listed = new ArrayList<String>();
        for (var entry : queue.get("content")) {
            if (entry.get("id").asString().equals(payoutId)) {
                assertThat(entry.get("organizerId").asString()).isEqualTo(organizer.getId().toString());
                assertThat(entry.get("organizerEmail").asString()).isEqualTo(organizer.getEmail());
                assertThat(entry.get("amount").decimalValue()).isEqualByComparingTo("20.00");
                assertThat(entry.get("requestedAt").asString()).isNotBlank();
                assertThat(entry.get("pixKey").asString()).isEqualTo("m***@example.com");
            }
            listed.add(entry.get("id").asString());
        }
        assertThat(listed).contains(payoutId);
    }

    @Test
    void payoutsAfterAPaidOneGoStraightToRequestedUnlessAboveThreshold() throws Exception {
        releasedSales(organizer, "3000.00", "3000.00");
        registerAccountWithPayoutHistory(organizer);

        var small = body(requestPayout(organizer, "5000.00", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))).get("id").asString();
        cancel(organizer, small).andExpect(status().isOk());

        requestPayout(organizer, "5000.01", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void approvedPayoutIsProcessedAndPaid() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var admin = user("ADMIN");
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();

        approve(admin, payoutId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        processing.processAll();

        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM payouts WHERE id = ?::uuid", UUID.class, payoutId))
                .isEqualTo(admin.getId());
        assertThat(available(organizer)).isEqualByComparingTo("45.00");
        approve(admin, payoutId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-payout-state"));
    }

    @Test
    void rejectedPayoutIsReversedAndShowsTheReason() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var admin = user("ADMIN");
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();

        reject(admin, payoutId, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        reject(admin, payoutId, "  Documento divergente  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Documento divergente"));

        assertThat(reversals(payoutId)).isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("95.00");
        assertThat(balance(organizer).get("inPayout").decimalValue()).isEqualByComparingTo("0");
        mockMvc.perform(get("/organizer/payouts").header("Authorization", bearer(organizer)))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Documento divergente"));
        reject(admin, payoutId, "outra vez").andExpect(status().isConflict());
        cancel(organizer, payoutId).andExpect(status().isConflict());
        assertThat(reversals(payoutId)).isEqualTo(1);
    }

    @Test
    void organizerCanCancelAPayoutUnderReview() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();

        cancel(organizer, payoutId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reversals(payoutId)).isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("95.00");
        approve(user("ADMIN"), payoutId).andExpect(status().isConflict());
    }

    @Test
    void approveRejectAndCancelRaceHasASingleWinner() throws Exception {
        var admin = user("ADMIN");
        for (int round = 0; round < 3; round++) {
            var owner = user("ORGANIZER");
            releasedSales(owner, "100.00");
            registerAccount(owner);
            var payoutId = UUID.fromString(body(requestPayout(owner, "50.00", PASSWORD, null)).get("id").asString());

            var outcomes = race(List.of(
                    () -> adminService.approve(payoutId, admin),
                    () -> adminService.reject(payoutId, "Suspeita de fraude", admin),
                    () -> requestService.cancel(owner, payoutId)));

            var winners = outcomes.stream().filter(PayoutDTO.class::isInstance).map(PayoutDTO.class::cast).toList();
            var losers = outcomes.stream().filter(ProblemException.class::isInstance).map(ProblemException.class::cast).toList();
            assertThat(winners).hasSize(1);
            assertThat(losers).hasSize(2)
                    .allSatisfy(loser -> assertThat(loser.getType()).isEqualTo(ProblemType.INVALID_PAYOUT_STATE));
            var winner = winners.get(0).status();
            assertThat(payoutStatus(payoutId.toString())).isEqualTo(winner.name());
            assertThat(reversals(payoutId.toString())).isEqualTo(winner == PayoutStatus.REQUESTED ? 0 : 1);
        }
    }

    @Test
    void adminEndpointsAreForbiddenForOrganizersAndBuyers() throws Exception {
        var payoutId = UUID.randomUUID();
        for (User user : List.of(organizer, buyer)) {
            var auth = bearer(user);
            mockMvc.perform(get("/admin/payouts").header("Authorization", auth)).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/payouts/" + payoutId + "/approve").header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/payouts/" + payoutId + "/reject").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/organizers").param("email", "a@b.com").header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/organizers/" + organizer.getId() + "/payout-block").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/admin/organizers/" + organizer.getId() + "/payout-block").header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/audit").header("Authorization", auth)).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_blocks WHERE organizer_id = ?",
                Integer.class, organizer.getId())).isZero();
    }

    private ResultActions approve(User admin, String payoutId) throws Exception {
        return mockMvc.perform(post("/admin/payouts/" + payoutId + "/approve").header("Authorization", bearer(admin)));
    }

    private ResultActions reject(User admin, String payoutId, String reason) throws Exception {
        return mockMvc.perform(post("/admin/payouts/" + payoutId + "/reject")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions cancel(User owner, String payoutId) throws Exception {
        return mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(owner)));
    }

    private String payoutStatus(String payoutId) {
        return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?::uuid", String.class, payoutId);
    }

    private int reversals(String payoutId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE payout_id = ?::uuid AND type = 'PAYOUT_REVERSAL'",
                Integer.class, payoutId);
    }

    private static List<Object> race(List<Callable<PayoutDTO>> actions) throws Exception {
        var ready = new CountDownLatch(actions.size());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(actions.size());
        try {
            var futures = new ArrayList<Future<Object>>();
            for (var action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return action.call();
                    } catch (ProblemException ex) {
                        return ex;
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            var outcomes = new ArrayList<Object>();
            for (var future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
