package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.event.EventCancellationDTO;
import com.lutfy.ticketfy.event.EventCancellationService;
import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.InvalidTicketStateException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.TicketNotFoundException;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTransferConcurrencyIntegrationTest extends TicketTransferTestBase {

    @Autowired
    private EventCancellationService cancellationService;

    @RepeatedTest(5)
    void onlyOneOfTwoConcurrentTransfersWins() throws Exception {
        var buyer = user("USER");
        var first = user("USER");
        var second = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        var oldCode = code(ticketId);

        var results = race(
                () -> transferService.transfer(ticketId, to(first), buyer),
                () -> transferService.transfer(ticketId, to(second), buyer));

        assertOnlyExpectedFailures(results);
        assertThat(successes(results)).isEqualTo(1);
        assertThat(transfers(ticketId)).isEqualTo(1);
        var winner = jdbc.queryForObject("SELECT to_user_id FROM ticket_transfers WHERE ticket_id = ?", UUID.class, ticketId);
        assertThat(winner).isIn(first.getId(), second.getId());
        assertThat(owner(ticketId)).isEqualTo(winner);
        assertThat(code(ticketId)).isNotEqualTo(oldCode);
        assertThat(jdbc.queryForObject("SELECT transfer_count FROM tickets WHERE id = ?", Integer.class, ticketId))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    void transferAndCheckInNeverBothSucceed() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        var oldCode = code(ticketId);

        var results = race(
                () -> transferService.transfer(ticketId, to(recipient), buyer),
                () -> ticketService.checkIn(oldCode, organizer));

        assertOnlyExpectedFailures(results);
        assertThat(successes(results)).isEqualTo(1);
        if (results.get(0) == null) {
            assertThat(ticketStatus(ticketId)).isEqualTo("VALID");
            assertThat(owner(ticketId)).isEqualTo(recipient.getId());
            assertThat(code(ticketId)).isNotEqualTo(oldCode);
            assertThat(jdbc.queryForObject("SELECT used_at FROM tickets WHERE id = ?", Object.class, ticketId)).isNull();
        } else {
            assertThat(ticketStatus(ticketId)).isEqualTo("USED");
            assertThat(owner(ticketId)).isEqualTo(buyer.getId());
            assertThat(code(ticketId)).isEqualTo(oldCode);
            assertThat(transfers(ticketId)).isZero();
        }
    }

    @RepeatedTest(5)
    void transferAndBuyerRefundNeverBothSucceed() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var orderId = paidOrder(buyer, 1);
        var ticketId = ticketOf(orderId);

        var results = race(
                () -> transferService.transfer(ticketId, to(recipient), buyer),
                () -> orderService.refund(orderId, buyer));

        assertOnlyExpectedFailures(results);
        assertThat(successes(results)).isEqualTo(1);
        if (results.get(0) == null) {
            assertThat(orderStatus(orderId)).isEqualTo("PAID");
            assertThat(ticketStatus(ticketId)).isEqualTo("VALID");
            assertThat(owner(ticketId)).isEqualTo(recipient.getId());
            assertThat(transfers(ticketId)).isEqualTo(1);
        } else {
            assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
            assertThat(ticketStatus(ticketId)).isEqualTo("CANCELLED");
            assertThat(owner(ticketId)).isEqualTo(buyer.getId());
            assertThat(transfers(ticketId)).isZero();
        }
    }

    @RepeatedTest(5)
    void transferAndEventCancellationLeaveTheTicketCancelledAndTheBuyerRefunded() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var orderId = paidOrder(buyer, 1);
        var ticketId = ticketOf(orderId);

        var results = race(
                () -> transferService.transfer(ticketId, to(recipient), buyer),
                () -> cancellationService.cancel(eventId, new EventCancellationDTO(null), organizer));

        assertOnlyExpectedFailures(results);
        assertThat(results.get(1)).isNull();
        assertThat(ticketStatus(ticketId)).isEqualTo("CANCELLED");
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("REFUNDED");
        if (results.get(0) == null) {
            assertThat(owner(ticketId)).isEqualTo(recipient.getId());
            assertThat(transfers(ticketId)).isEqualTo(1);
        } else {
            assertThat(owner(ticketId)).isEqualTo(buyer.getId());
            assertThat(transfers(ticketId)).isZero();
        }
    }

    private List<Throwable> race(Callable<?> left, Callable<?> right) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
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

    private static void assertOnlyExpectedFailures(List<Throwable> results) {
        assertThat(results).allSatisfy(result -> {
            if (result != null) {
                assertThat(result).isInstanceOfAny(InvalidTicketStateException.class, TicketNotFoundException.class,
                        InvalidEventStateException.class, ProblemException.class);
            }
        });
    }

    private static long successes(List<Throwable> results) {
        return results.stream().filter(result -> result == null).count();
    }
}
