package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutConcurrencyIntegrationTest extends PayoutTestBase {

    private static final int THREADS = 8;

    @Autowired
    private PayoutRequestService service;

    @Test
    void simultaneousRequestsAcceptExactlyOne() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);

        var outcomes = runConcurrently(i -> UUID.randomUUID().toString(), "95.00");

        var accepted = outcomes.stream().filter(PayoutDTO.class::isInstance).count();
        var rejected = outcomes.stream()
                .filter(ProblemException.class::isInstance)
                .map(ProblemException.class::cast)
                .toList();
        assertThat(accepted).isEqualTo(1);
        assertThat(rejected).hasSize(THREADS - 1)
                .allSatisfy(problem -> {
                    assertThat(problem.getType()).isEqualTo(ProblemType.PAYOUT_IN_PROGRESS);
                    assertThat(problem.getType().status()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(count("SELECT COUNT(*) FROM payouts WHERE organizer_id = ?")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM organizer_ledger_entries WHERE organizer_id = ? AND type = 'PAYOUT_DEBIT'"))
                .isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("0").isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void simultaneousDoubleClickWithSameKeyCreatesOnePayout() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var key = UUID.randomUUID().toString();

        var outcomes = runConcurrently(i -> key, "50.00");

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(PayoutDTO.class));
        assertThat(outcomes.stream().map(outcome -> ((PayoutDTO) outcome).id()).distinct()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM payouts WHERE organizer_id = ?")).isEqualTo(1);
        assertThat(available(organizer)).isEqualByComparingTo("45.00");
    }

    @Test
    void databaseRejectsASecondPayoutInProgress() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        requestPayout(organizer, "20.00", PASSWORD, null);
        var existing = jdbc.queryForMap("SELECT * FROM payouts WHERE organizer_id = ?", organizer.getId());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payouts (organizer_id, amount, status, document_type, document, holder_name,
                                     pix_key_type, pix_key, requested_at)
                VALUES (?, 20.00, 'PROCESSING', 'CPF', ?, 'Maria', 'EMAIL', ?, NOW())
                """, organizer.getId(), existing.get("document"), existing.get("pix_key")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private List<Object> runConcurrently(Function<Integer, String> idempotencyKey, String amount) throws Exception {
        var ready = new CountDownLatch(THREADS);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(THREADS);
        try {
            var futures = new ArrayList<Future<Object>>();
            for (int i = 0; i < THREADS; i++) {
                var key = idempotencyKey.apply(i);
                User requester = organizer;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return service.request(requester, new PayoutRequestDTO(new BigDecimal(amount), PASSWORD), key);
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

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, organizer.getId());
    }
}
