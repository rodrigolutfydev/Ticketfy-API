package com.lutfy.ticketfy.payout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutRequestIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private PayoutProcessingService processing;

    @Test
    void requiresPayoutAccount() throws Exception {
        releasedSales(organizer, "100.00");

        requestPayout(organizer, "50.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-account-required"));
    }

    @Test
    void wrongPasswordIsForbiddenAndCreatesNothing() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);

        requestPayout(organizer, "50.00", "wrong-password", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-password"));

        assertThat(payoutCount()).isZero();
    }

    @Test
    void keyChangeBlocksPayoutsFor48Hours() throws Exception {
        var released = releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        saveAccount(organizer, "CPF", CPF, "CPF", CPF, PASSWORD);
        var blockedUntil = released.plus(Duration.ofHours(48));

        travelTo(blockedUntil.minusSeconds(1));
        requestPayout(organizer, "50.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-account-cooldown"))
                .andExpect(jsonPath("$.blockedUntil").value(blockedUntil.toString()));

        travelTo(blockedUntil);
        requestPayout(organizer, "50.00", PASSWORD, null).andExpect(status().isCreated());
    }

    @Test
    void onlyOnePayoutInProgress() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        requestPayout(organizer, "20.00", PASSWORD, null).andExpect(status().isCreated());

        requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-in-progress"));
    }

    @Test
    void negativeAvailableBalanceBlocksPayouts() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var sale = jdbc.queryForMap("""
                SELECT event_id, order_id FROM organizer_ledger_entries
                 WHERE organizer_id = ? AND type = 'SALE_CREDIT'
                """, organizer.getId());
        jdbc.update("""
                INSERT INTO organizer_ledger_entries (organizer_id, event_id, order_id, type, amount)
                VALUES (?, ?, ?, 'REFUND_DEBIT', -120.00)
                """, organizer.getId(), sale.get("event_id"), sale.get("order_id"));

        requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "negative-available-balance"));
    }

    @Test
    void amountMustBeAtLeastTheMinimumAndAtMostTheAvailable() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);

        requestPayout(organizer, "19.99", PASSWORD, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-below-minimum"))
                .andExpect(jsonPath("$.minAmount").value(20.00));
        requestPayout(organizer, "95.01", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-exceeds-available"));
        requestPayout(organizer, "0", PASSWORD, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"));
        requestPayout(organizer, "20.001", PASSWORD, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"));

        requestPayout(organizer, "95.00", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        assertThat(available(organizer)).isEqualByComparingTo("0");
    }

    @Test
    void pendingSalesAreNotAvailable() throws Exception {
        var released = releasedSales(organizer, "100.00");
        travelTo(released.minus(Duration.ofMinutes(2)));
        registerAccountWithPayoutHistory(organizer);

        requestPayout(organizer, "20.00", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-exceeds-available"));
    }

    @Test
    void sameIdempotencyKeyCreatesASinglePayout() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var key = UUID.randomUUID().toString();

        var first = body(requestPayout(organizer, "30.00", PASSWORD, key).andExpect(status().isCreated()));
        var second = body(requestPayout(organizer, "30.00", PASSWORD, key).andExpect(status().isCreated()));

        assertThat(second.get("id").asString()).isEqualTo(first.get("id").asString());
        assertThat(payoutCount()).isEqualTo(1);
        assertThat(ledgerCount("PAYOUT_DEBIT")).isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("65.00");
    }

    @Test
    void requestDebitsTheLedgerAndShowsTheAmountInPayout() throws Exception {
        releasedSales(organizer, "100.00", "50.00");
        registerAccount(organizer);

        var payout = body(requestPayout(organizer, "40.00", PASSWORD, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.pixKeyType").value("EMAIL"))
                .andExpect(jsonPath("$.pixKey").value("m***@example.com")));

        var balance = balance(organizer);
        assertThat(balance.get("available").decimalValue()).isEqualByComparingTo("102.50");
        assertThat(balance.get("inPayout").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(balance.get("total").decimalValue()).isEqualByComparingTo("102.50");
        assertThat(balance.get("minPayoutAmount").decimalValue()).isEqualByComparingTo("20.00");
        mockMvc.perform(get("/organizer/ledger").header("Authorization", bearer(organizer)))
                .andExpect(jsonPath("$.content[0].type").value("PAYOUT_DEBIT"))
                .andExpect(jsonPath("$.content[0].amount").value(-40.00))
                .andExpect(jsonPath("$.content[0].payoutId").value(payout.get("id").asString()))
                .andExpect(jsonPath("$.content[0].eventId").doesNotExist())
                .andExpect(jsonPath("$.content[0].orderId").doesNotExist());
        var row = jdbc.queryForMap("SELECT document, pix_key FROM payouts WHERE organizer_id = ?", organizer.getId());
        assertThat((String) row.get("document")).startsWith("v1:").doesNotContain(CPF);
        assertThat((String) row.get("pix_key")).startsWith("v1:").doesNotContain("maria.silva");
    }

    @Test
    void cancellingReversesTheDebit() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "60.00", PASSWORD, null)).get("id").asString();

        mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(user("ORGANIZER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "payout-not-found"));
        mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        var balance = balance(organizer);
        assertThat(balance.get("available").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(balance.get("inPayout").decimalValue()).isEqualByComparingTo("0");
        assertThat(ledgerCount("PAYOUT_REVERSAL")).isEqualTo(1);
        mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-payout-state"));
        requestPayout(organizer, "95.00", PASSWORD, null).andExpect(status().isCreated());
    }

    @Test
    void jobPaysRequestedPayouts() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();

        assertThat(processing.processAll()).isPositive();

        var row = jdbc.queryForMap("SELECT status, transfer_reference, finished_at FROM payouts WHERE id = ?::uuid", payoutId);
        assertThat(row.get("status")).isEqualTo("PAID");
        assertThat(row.get("transfer_reference")).isEqualTo("SIM-PAYOUT-" + payoutId);
        assertThat(row.get("finished_at")).isNotNull();
        assertThat(ledgerCount("PAYOUT_REVERSAL")).isZero();
        var balance = balance(organizer);
        assertThat(balance.get("available").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(balance.get("inPayout").decimalValue()).isEqualByComparingTo("0");
        mockMvc.perform(post("/organizer/payouts/" + payoutId + "/cancel").header("Authorization", bearer(organizer)))
                .andExpect(status().isConflict());
    }

    @Test
    void failedTransferIsReversed() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();
        doReturn(new PayoutGateway.TransferResult(PayoutGateway.TransferStatus.FAILED, null, "Pix key closed"))
                .when(payoutGateway).transfer(any());

        processing.processAll();

        mockMvc.perform(get("/organizer/payouts").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(payoutId))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].failureReason").value("Pix key closed"));
        assertThat(ledgerCount("PAYOUT_REVERSAL")).isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("95.00");
    }

    @Test
    void stuckPayoutIsResumedWithoutTransferringTwice() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("connection reset after transfer");
        }).when(payoutGateway).transfer(any());

        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PROCESSING");

        doCallRealMethod().when(payoutGateway).transfer(any());
        travel(Duration.ofMinutes(5));
        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PROCESSING");

        travel(Duration.ofMinutes(6));
        processing.processAll();

        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        verify(payoutGateway, times(1)).transfer(argThat(request -> request.payoutId().toString().equals(payoutId)));
        assertThat(available(organizer)).isEqualByComparingTo("45.00");
    }

    @Test
    void payoutStuckBeforeReachingTheProviderIsTransferredOnce() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "50.00", PASSWORD, null)).get("id").asString();
        doThrow(new IllegalStateException("provider unavailable")).when(payoutGateway).transfer(any());

        processing.processAll();
        doCallRealMethod().when(payoutGateway).transfer(any());
        travel(Duration.ofMinutes(11));
        processing.processAll();

        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        assertThat(payoutGateway.findTransfer(payoutId)).isPresent();
    }

    @Test
    void ledgerSumStillMatchesTheBalance() throws Exception {
        releasedSales(organizer, "100.00", "200.00");
        registerAccountWithPayoutHistory(organizer);
        requestPayout(organizer, "30.00", PASSWORD, null).andExpect(status().isCreated());
        processing.processAll();
        var cancelled = body(requestPayout(organizer, "20.00", PASSWORD, null)).get("id").asString();
        mockMvc.perform(post("/organizer/payouts/" + cancelled + "/cancel").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
        doReturn(new PayoutGateway.TransferResult(PayoutGateway.TransferStatus.FAILED, null, "rejected"))
                .when(payoutGateway).transfer(any());
        requestPayout(organizer, "40.00", PASSWORD, null).andExpect(status().isCreated());
        processing.processAll();
        requestPayout(organizer, "25.00", PASSWORD, null).andExpect(status().isCreated());
        var pendingEvent = insertEvent(organizer.getId(), clock.instant().plus(Duration.ofDays(5)),
                clock.instant().plus(Duration.ofDays(5)).plus(Duration.ofHours(3)));
        jdbc.update("""
                INSERT INTO organizer_ledger_entries (organizer_id, event_id, order_id, type, amount)
                SELECT ?, ?, order_id, 'REFUND_DEBIT', -1.00 FROM organizer_ledger_entries
                 WHERE organizer_id = ? AND type = 'SALE_CREDIT' LIMIT 1
                """, organizer.getId(), pendingEvent, organizer.getId());

        var balance = balance(organizer);
        var ledgerSum = jdbc.queryForObject(
                "SELECT SUM(amount) FROM organizer_ledger_entries WHERE organizer_id = ?", BigDecimal.class, organizer.getId());
        var released = new BigDecimal("285.00");
        var paid = new BigDecimal("30.00");
        assertThat(balance.get("total").decimalValue()).isEqualByComparingTo(ledgerSum);
        assertThat(balance.get("pending").decimalValue()
                .add(balance.get("available").decimalValue())
                .add(balance.get("held").decimalValue()))
                .isEqualByComparingTo(ledgerSum);
        assertThat(balance.get("pending").decimalValue()).isEqualByComparingTo("-1.00");
        assertThat(balance.get("inPayout").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(balance.get("available").decimalValue())
                .isEqualByComparingTo(released.subtract(paid).subtract(new BigDecimal("25.00")));
    }

    @Test
    void historyListsNewestFirstWithMaskedKeys() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var first = body(requestPayout(organizer, "20.00", PASSWORD, null)).get("id").asString();
        mockMvc.perform(post("/organizer/payouts/" + first + "/cancel").header("Authorization", bearer(organizer)));
        travel(Duration.ofMinutes(1));
        var second = body(requestPayout(organizer, "30.00", PASSWORD, null)).get("id").asString();

        var response = mockMvc.perform(get("/organizer/payouts").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("maria.silva", CPF);
        mockMvc.perform(get("/organizer/payouts").header("Authorization", bearer(user("ORGANIZER"))))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    private int payoutCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payouts WHERE organizer_id = ?", Integer.class, organizer.getId());
    }

    private int ledgerCount(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM organizer_ledger_entries WHERE organizer_id = ? AND type = ?",
                Integer.class, organizer.getId(), type);
    }

    private String payoutStatus(String payoutId) {
        return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?::uuid", String.class, payoutId);
    }
}
