package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionConcurrencyIntegrationTest extends PrivacyTestBase {

    @Autowired
    private AccountDeletionService deletionService;

    @RepeatedTest(10)
    void deletionAndPaymentOfAPendingOrderNeverBothSucceed() throws Exception {
        var organizer = person("ORGANIZER", "Nina");
        var lot = insertTicketType(upcomingEvent(organizer), "35.00");
        var user = person("USER", "Otto");
        var orderId = pendingOrder(user, lot, 2);

        var results = race(
                () -> {
                    deletionService.delete(user, new AccountDeletionRequestDTO(PASSWORD, "EXCLUIR"));
                    return null;
                },
                () -> paymentService.paySimulated(orderId, user));

        var deletion = results.get(0);
        var payment = results.get(1);
        var deleted = jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class,
                user.getId());
        var orderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        var tickets = jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE order_id = ?", Integer.class, orderId);
        var approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'APPROVED'", Integer.class, orderId);
        var sold = jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, lot);

        assertThat(deletion == null).isNotEqualTo(payment == null);
        if (deletion == null) {
            assertThat(deleted).isTrue();
            assertThat(orderStatus).isEqualTo("CANCELLED");
            assertThat(tickets).isZero();
            assertThat(approved).isZero();
            assertThat(sold).isZero();
            assertThat(payment).isInstanceOfAny(ProblemException.class, InvalidOrderStateException.class);
            if (payment instanceof ProblemException problem) {
                assertThat(problem.getType()).isEqualTo(ProblemType.SESSION_EXPIRED);
            }
        } else {
            assertThat(deleted).isFalse();
            assertThat(orderStatus).isEqualTo("PAID");
            assertThat(tickets).isEqualTo(2);
            assertThat(approved).isEqualTo(1);
            assertThat(sold).isEqualTo(2);
            assertThat(deletion).isInstanceOf(ProblemException.class);
            assertThat(((ProblemException) deletion).getType()).isEqualTo(ProblemType.ACCOUNT_HAS_UPCOMING_TICKETS);
        }
    }

    private List<Throwable> race(Callable<?> left, Callable<?> right) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(left, right).stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            task.call();
                            return (Throwable) null;
                        } catch (RuntimeException ex) {
                            return (Throwable) ex;
                        }
                    }))
                    .toList();
            ready.await();
            start.countDown();
            var results = new ArrayList<Throwable>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
