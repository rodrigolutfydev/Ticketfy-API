package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.order.OrderDetailsDTO;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CouponConcurrencyIntegrationTest extends CouponTestBase {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CouponService couponService;

    @Test
    void onlyOneOfTenBuyersGetsTheLastUse() throws Exception {
        var lot = lot(eventId, "50.00", 100);
        var couponId = coupon(eventId, "ULTIMO", "PERCENT", "50.00", 1);
        var buyers = new ArrayList<User>();
        for (int i = 0; i < 10; i++) buyers.add(user("USER"));

        var ready = new CountDownLatch(buyers.size());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(buyers.size());
        var successes = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unexpected = new ConcurrentLinkedQueue<Throwable>();

        for (var buyer : buyers) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    order(buyer, lot, 2, "ULTIMO");
                    successes.incrementAndGet();
                } catch (ProblemException ex) {
                    if (ex.getType() == ProblemType.INVALID_COUPON) rejected.incrementAndGet();
                    else unexpected.add(ex);
                } catch (Throwable ex) {
                    unexpected.add(ex);
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(9);
        assertThat(uses(couponId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM orders WHERE coupon_id = ?", couponId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_items WHERE ticket_type_id = ?", lot)).isEqualTo(1);
        assertThat(count("SELECT quantity_sold FROM ticket_types WHERE id = ?", lot)).isEqualTo(2);
        assertThat(count("""
                SELECT COUNT(*) FROM orders
                 WHERE coupon_id = ? AND subtotal_amount = 100.00 AND discount_amount = 50.00 AND total_amount = 50.00
                """, couponId)).isEqualTo(1);
    }

    @Test
    void orderWaitingOnTheCouponUsesTheValuesCommittedBeforeIt() throws Exception {
        var lot = lot(eventId, "100.00", 10);
        var couponId = coupon(eventId, "MUDANDO", "PERCENT", "10.00", null);
        var buyer = user("USER");
        var executor = Executors.newSingleThreadExecutor();
        var tx = new TransactionTemplate(transactionManager);

        var result = tx.execute(status -> {
            couponService.update(eventId, couponId, settings(DiscountType.PERCENT, "50.00", true), organizer);
            var pending = executor.submit(() -> order(buyer, lot, 1, "MUDANDO"));
            waitUntilBlockedOnLock();
            return pending;
        });
        OrderDetailsDTO order = result.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(order.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(order.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(uses(couponId)).isEqualTo(1);
    }

    @Test
    void expirationWhileTheOrganizerDeactivatesKeepsTheCounterRight() throws Exception {
        var lot = lot(eventId, "100.00", 10);
        var couponId = coupon(eventId, "DESLIGA", "PERCENT", "10.00", 5);
        var order = order(user("USER"), lot, 1, "DESLIGA");
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), order.id());
        var executor = Executors.newSingleThreadExecutor();
        var tx = new TransactionTemplate(transactionManager);

        var expiration = tx.execute(status -> {
            couponService.update(eventId, couponId, settings(DiscountType.PERCENT, "10.00", false), organizer);
            var pending = executor.submit(() -> orderService.expireOrder(order.id()));
            waitUntilBlockedOnLock();
            return pending;
        });
        assertThat(expiration.get(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(uses(couponId)).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM coupons WHERE id = ?", Boolean.class, couponId)).isFalse();
        assertThat(orderStatus(order.id())).isEqualTo("EXPIRED");
    }

    @Test
    void organizerCannotChangeTheDiscountOnceAConcurrentOrderUsedIt() throws Exception {
        var lot = lot(eventId, "100.00", 10);
        var couponId = coupon(eventId, "CORRIDA", "PERCENT", "10.00", null);
        var executor = Executors.newSingleThreadExecutor();
        var tx = new TransactionTemplate(transactionManager);

        var race = tx.execute(status -> {
            var orderId = order(user("USER"), lot, 1, "CORRIDA").id();
            var pending = executor.submit(() -> {
                try {
                    couponService.update(eventId, couponId, settings(DiscountType.PERCENT, "90.00", true), organizer);
                    return null;
                } catch (ProblemException ex) {
                    return ex.getType();
                }
            });
            waitUntilBlockedOnLock();
            return new Race(orderId, pending);
        });
        var outcome = race.update().get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(outcome).isEqualTo(ProblemType.COUPON_ALREADY_USED);
        assertThat(orderColumn(race.orderId(), "discount_amount")).isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject("SELECT discount_value FROM coupons WHERE id = ?", BigDecimal.class, couponId))
                .isEqualByComparingTo("10.00");
    }

    private record Race(UUID orderId, Future<ProblemType> update) {
    }

    private CouponUpdateDTO settings(DiscountType type, String value, boolean active) {
        return new CouponUpdateDTO(type, new BigDecimal(value), null, null, null, active);
    }

    private void waitUntilBlockedOnLock() {
        var deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            var waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
                    Long.class);
            if (waiting != null && waiting > 0) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        throw new AssertionError("The concurrent transaction never waited for the coupon lock");
    }
}
