package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.ticket.TicketTransferRequestDTO;
import com.lutfy.ticketfy.ticket.TicketTransferService;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BuyerLimitIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private TicketTransferService transfers;

    private UUID eventId;

    @BeforeEach
    void createEvent() {
        var startsAt = clock.instant().plus(Duration.ofDays(30));
        eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
    }

    @Test
    void lotLimitHoldsAcrossOrdersOfTheSameBuyer() {
        var lot = lot("50.00", 3);
        order(buyer, lot, 2, null);
        order(buyer, lot, 1, null);

        assertThatThrownBy(() -> order(buyer, lot, 1, null))
                .isInstanceOfSatisfying(ProblemException.class, problem -> {
                    assertThat(problem.getType()).isEqualTo(ProblemType.MAX_PER_BUYER_EXCEEDED);
                    assertThat(problem.getProperties()).containsEntry("maxPerBuyer", 3).containsEntry("remaining", 0);
                });
        var other = user("USER");
        assertThatThrownBy(() -> order(other, lot, 4, null))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getProperties()).containsEntry("remaining", 3));
        assertThat(quantitySold(lot)).isEqualTo(3);
        assertThat(counted(lot, buyer)).isEqualTo(3);
    }

    @Test
    void freeLotsAreLimitedToo() {
        var lot = lot("0.00", 2);
        var orderId = order(buyer, lot, 2, null);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");

        assertThatThrownBy(() -> order(buyer, lot, 1, null))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.MAX_PER_BUYER_EXCEEDED));
    }

    @Test
    void couponLimitHoldsAcrossOrdersWithoutCountingAsFailedAttempt() throws Exception {
        var lot = lot("50.00", null);
        var couponId = coupon("VOLTA10", 10, 2);
        order(buyer, lot, 1, "volta10");
        order(buyer, lot, 1, "volta10");

        for (int attempt = 0; attempt < 12; attempt++) {
            assertThatThrownBy(() -> order(buyer, lot, 1, "volta10"))
                    .isInstanceOfSatisfying(ProblemException.class, problem -> {
                        assertThat(problem.getType()).isEqualTo(ProblemType.COUPON_BUYER_LIMIT_REACHED);
                        assertThat(problem.getProperties()).containsEntry("maxUsesPerBuyer", 2);
                    });
        }
        mockMvc.perform(post("/events/{id}/coupons/preview", eventId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"VOLTA10\",\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":1}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "coupon-buyer-limit-reached"))
                .andExpect(jsonPath("$.maxUsesPerBuyer").value(2));
        assertThat(couponUses(couponId)).isEqualTo(2);
        assertThat(couponBuyerUses(couponId, buyer)).isEqualTo(2);
        assertThat(quantitySold(lot)).isEqualTo(2);
    }

    @Test
    void cancelledAndExpiredOrdersGiveTheLimitsBack() {
        var lot = lot("50.00", 2);
        var couponId = coupon("UMAVEZ", 10, 1);
        var cancelled = order(buyer, lot, 2, "umavez");
        orderService.cancel(cancelled, buyer);
        assertThat(counted(lot, buyer)).isZero();
        assertThat(couponBuyerUses(couponId, buyer)).isZero();

        var expiring = order(buyer, lot, 2, "umavez");
        travel(Duration.ofMinutes(16));
        assertThat(orderService.expireOrder(expiring)).isTrue();
        assertThat(counted(lot, buyer)).isZero();
        assertThat(couponBuyerUses(couponId, buyer)).isZero();

        order(buyer, lot, 2, "umavez");
        assertThat(counted(lot, buyer)).isEqualTo(2);
        assertThat(couponBuyerUses(couponId, buyer)).isEqualTo(1);
    }

    @Test
    void refundGivesTheLotLimitBackButNotTheCouponUse() {
        var lot = lot("50.00", 2);
        var couponId = coupon("REEMBOLSO", 10, 1);
        var paid = order(buyer, lot, 2, "reembolso");
        paymentService.paySimulated(paid, buyer);

        orderService.refund(paid, buyer);

        assertThat(counted(lot, buyer)).isZero();
        assertThat(couponBuyerUses(couponId, buyer)).isEqualTo(1);
        order(buyer, lot, 2, null);
        assertThatThrownBy(() -> order(buyer, lot, 1, null))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.MAX_PER_BUYER_EXCEEDED));
        var other = lot("30.00", null);
        assertThatThrownBy(() -> order(buyer, other, 1, "reembolso"))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.COUPON_BUYER_LIMIT_REACHED));
    }

    @Test
    void differentBuyersDoNotShareTheLimit() {
        var lot = lot("50.00", 2);
        var couponId = coupon("CADAUM", 10, 1);
        var other = user("USER");
        order(buyer, lot, 2, "cadaum");
        order(other, lot, 2, "cadaum");

        assertThat(counted(lot, buyer)).isEqualTo(2);
        assertThat(counted(lot, other)).isEqualTo(2);
        assertThat(couponBuyerUses(couponId, buyer)).isEqualTo(1);
        assertThat(couponBuyerUses(couponId, other)).isEqualTo(1);
    }

    @Test
    void transferredTicketsDoNotConsumeTheRecipientLimitNorFreeTheSenderLimit() {
        var lot = lot("50.00", 2);
        var recipient = user("USER");
        var paid = order(buyer, lot, 2, null);
        paymentService.paySimulated(paid, buyer);
        var ticketId = jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ? LIMIT 1", UUID.class, paid);

        transfers.transfer(ticketId, new TicketTransferRequestDTO(recipient.getEmail(), PASSWORD), buyer);

        order(recipient, lot, 2, null);
        assertThat(counted(lot, recipient)).isEqualTo(2);
        assertThatThrownBy(() -> order(buyer, lot, 1, null))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.MAX_PER_BUYER_EXCEEDED));
        assertThat(counted(lot, buyer)).isEqualTo(2);
    }

    @Test
    void concurrentOrdersOfTheSameBuyerNeverPassTheLotLimit() throws Exception {
        var lot = lot("50.00", 3);
        var outcomes = race(8, () -> order(buyer, lot, 1, null));

        assertThat(outcomes.stream().filter(UUID.class::isInstance)).hasSize(3);
        assertThat(outcomes.stream().filter(outcome -> !(outcome instanceof UUID)))
                .allSatisfy(outcome -> assertThat(outcome).isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.MAX_PER_BUYER_EXCEEDED)));
        assertThat(counted(lot, buyer)).isEqualTo(3);
        assertThat(quantitySold(lot)).isEqualTo(3);
        assertThat(itemsInOpenOrders(lot, buyer)).isEqualTo(3);
    }

    @Test
    void concurrentOrdersOfTheSameBuyerNeverPassTheCouponLimit() throws Exception {
        var lot = lot("50.00", null);
        var couponId = coupon("CORRIDA", 10, 2);
        var outcomes = race(6, () -> order(buyer, lot, 1, "corrida"));

        assertThat(outcomes.stream().filter(UUID.class::isInstance)).hasSize(2);
        assertThat(outcomes.stream().filter(outcome -> !(outcome instanceof UUID)))
                .allSatisfy(outcome -> assertThat(outcome).isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.COUPON_BUYER_LIMIT_REACHED)));
        assertThat(couponBuyerUses(couponId, buyer)).isEqualTo(2);
        assertThat(couponUses(couponId)).isEqualTo(2);
        assertThat(quantitySold(lot)).isEqualTo(2);
    }

    @Test
    void loweringTheLimitKeepsExistingOrdersAndOnlyBlocksNewOnes() throws Exception {
        var lot = lot("50.00", 5);
        var couponId = coupon("REDUZ", 10, 3);
        var first = order(buyer, lot, 3, "reduz");
        var second = order(buyer, lot, 1, "reduz");

        updateLot(lot, "{\"maxPerBuyer\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerBuyer").value(2));
        mockMvc.perform(put("/events/{eventId}/coupons/{couponId}", eventId, couponId)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountType\":\"PERCENT\",\"discountValue\":10,\"active\":true,\"maxUsesPerBuyer\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUsesPerBuyer").value(1));

        assertThat(orderStatus(first)).isEqualTo("PENDING");
        assertThat(orderStatus(second)).isEqualTo("PENDING");
        assertThatThrownBy(() -> order(buyer, lot, 1, null))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getProperties()).containsEntry("remaining", 0));
        var otherLot = lot("20.00", null);
        assertThatThrownBy(() -> order(buyer, otherLot, 1, "reduz"))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.COUPON_BUYER_LIMIT_REACHED));

        updateLot(lot, "{\"maxPerBuyer\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerBuyer").doesNotExist());
        order(buyer, lot, 1, null);
        assertThat(counted(lot, buyer)).isEqualTo(5);
    }

    private ResultActions updateLot(UUID lot, String body) throws Exception {
        return mockMvc.perform(patch("/events/{eventId}/ticket-types/{id}", eventId, lot)
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID lot(String price, Integer maxPerBuyer) {
        var id = insertTicketType(eventId, price);
        jdbc.update("UPDATE ticket_types SET max_per_buyer = ? WHERE id = ?", maxPerBuyer, id);
        return id;
    }

    private UUID coupon(String code, int percent, Integer maxUsesPerBuyer) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coupons (id, event_id, code, discount_type, discount_value, max_uses_per_buyer)
                VALUES (?, ?, ?, 'PERCENT', ?, ?)
                """, id, eventId, code, percent, maxUsesPerBuyer);
        return id;
    }

    private UUID order(User who, UUID lot, int quantity, String couponCode) {
        return orderService.create(new OrderCreationDTO(List.of(new OrderItemRequestDTO(lot, quantity)), couponCode),
                null, who).id();
    }

    private List<Object> race(int threads, Callable<Object> action) throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var futures = new ArrayList<Future<Object>>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return action.call();
                    } catch (Exception ex) {
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

    private int quantitySold(UUID lot) {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, lot);
    }

    private int counted(UUID lot, User who) {
        return jdbc.queryForList("SELECT quantity FROM ticket_type_buyer_counts WHERE ticket_type_id = ? AND user_id = ?",
                Integer.class, lot, who.getId()).stream().findFirst().orElse(0);
    }

    private int couponUses(UUID couponId) {
        return jdbc.queryForObject("SELECT uses_count FROM coupons WHERE id = ?", Integer.class, couponId);
    }

    private int couponBuyerUses(UUID couponId, User who) {
        return jdbc.queryForList("SELECT uses FROM coupon_buyer_uses WHERE coupon_id = ? AND user_id = ?",
                Integer.class, couponId, who.getId()).stream().findFirst().orElse(0);
    }

    private int itemsInOpenOrders(UUID lot, User who) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                 WHERE oi.ticket_type_id = ? AND o.user_id = ? AND o.status IN ('PENDING', 'PAID')
                """, Integer.class, lot, who.getId());
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }
}
