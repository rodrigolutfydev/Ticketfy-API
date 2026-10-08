package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CouponIntegrationTest extends CouponTestBase {

    private static final String GENERIC_MESSAGE = "Coupon is invalid or unavailable";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void percentDiscountIsRoundedToCentsAndFeeUsesThePaidTotal() {
        var lot = lot(eventId, "33.33", 10);
        coupon(eventId, "PROMO15", "PERCENT", "15.00", null);
        var buyer = user("USER");

        var order = order(buyer, lot, 1, "  promo15 ");

        assertThat(order.subtotalAmount()).isEqualByComparingTo("33.33");
        assertThat(order.discountAmount()).isEqualByComparingTo("5.00");
        assertThat(order.totalAmount()).isEqualByComparingTo("28.33");
        assertThat(order.couponCode()).isEqualTo("PROMO15");
        assertThat(orderColumn(order.id(), "platform_fee")).isEqualByComparingTo("1.42");
        assertThat(orderColumn(order.id(), "net_amount")).isEqualByComparingTo("26.91");

        paymentService.paySimulated(order.id(), buyer);

        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE order_id = ?", BigDecimal.class, order.id()))
                .isEqualByComparingTo("28.33");
        assertThat(jdbc.queryForObject(
                "SELECT amount FROM organizer_ledger_entries WHERE order_id = ? AND type = 'SALE_CREDIT'",
                BigDecimal.class, order.id())).isEqualByComparingTo("26.91");
    }

    @Test
    void fixedDiscountAppliesToTheWholeOrderSubtotal() {
        var lot = lot(eventId, "80.00", 10);
        coupon(eventId, "MENOS12", "FIXED", "12.50", null);

        var order = order(user("USER"), lot, 2, "MENOS12");

        assertThat(order.subtotalAmount()).isEqualByComparingTo("160.00");
        assertThat(order.discountAmount()).isEqualByComparingTo("12.50");
        assertThat(order.totalAmount()).isEqualByComparingTo("147.50");
        assertThat(orderColumn(order.id(), "platform_fee")).isEqualByComparingTo("7.38");
        assertThat(orderColumn(order.id(), "net_amount")).isEqualByComparingTo("140.12");
    }

    @Test
    void fixedDiscountLargerThanSubtotalResultsInAFreeConfirmedOrder() {
        var lot = lot(eventId, "30.00", 10);
        coupon(eventId, "CORTESIA", "FIXED", "50.00", null);

        var order = order(user("USER"), lot, 1, "CORTESIA");

        assertThat(order.discountAmount()).isEqualByComparingTo("30.00");
        assertThat(order.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(order.status().name()).isEqualTo("PAID");
        assertThat(order.tickets()).hasSize(1);
        assertThat(orderColumn(order.id(), "platform_fee")).isEqualByComparingTo("0.00");
        assertThat(orderColumn(order.id(), "net_amount")).isEqualByComparingTo("0.00");
        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ?", order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?", order.id())).isZero();
    }

    @Test
    void orderKeepsItsDiscountAfterTheCouponChanges() throws Exception {
        var lot = lot(eventId, "100.00", 10);
        var couponId = coupon(eventId, "DEZ", "PERCENT", "10.00", 5);
        var buyer = user("USER");
        var order = order(buyer, lot, 1, "DEZ");

        mockMvc.perform(put("/events/{eventId}/coupons/{id}", eventId, couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountType":"PERCENT","discountValue":10.00,"maxUses":2,
                                 "endsAt":"2099-01-01T00:00:00Z","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.maxUses").value(2));

        mockMvc.perform(get("/orders/{id}", order.id()).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalAmount").value(100.00))
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(90.00))
                .andExpect(jsonPath("$.couponCode").value("DEZ"));

        paymentService.paySimulated(order.id(), buyer);
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE order_id = ?", BigDecimal.class, order.id()))
                .isEqualByComparingTo("90.00");
    }

    @Test
    void everyUnusableCodeIsRejectedWithTheSameMessage() throws Exception {
        var lot = lot(eventId, "50.00", 10);
        var now = Instant.now();
        coupon(eventId, "EXPIRADO", "PERCENT", "10.00", null, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)), true);
        coupon(eventId, "FUTURO", "PERCENT", "10.00", null, now.plus(Duration.ofDays(1)), null, true);
        coupon(eventId, "INATIVO", "PERCENT", "10.00", null, null, null, false);
        var exhausted = coupon(eventId, "ESGOTADO", "PERCENT", "10.00", 1);
        jdbc.update("UPDATE coupons SET uses_count = 1, first_used_at = NOW() WHERE id = ?", exhausted);
        coupon(insertEvent(organizer.getId()), "OUTRO", "PERCENT", "10.00", null);

        for (var code : new String[]{"NAOEXISTE", "EXPIRADO", "FUTURO", "INATIVO", "ESGOTADO", "OUTRO"}) {
            var buyer = user("USER");
            preview(buyer, code, lot, 1)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value(endsWith("/invalid-coupon")))
                    .andExpect(jsonPath("$.detail").value(GENERIC_MESSAGE));
            createOrder(buyer, code, lot, 1)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type").value(endsWith("/invalid-coupon")))
                    .andExpect(jsonPath("$.detail").value(GENERIC_MESSAGE));
        }

        assertThat(count("SELECT quantity_sold FROM ticket_types WHERE id = ?", lot)).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_items WHERE ticket_type_id = ?", lot)).isZero();
        assertThat(uses(exhausted)).isEqualTo(1);
    }

    @Test
    void previewShowsTheDiscountWithoutConsumingAUse() throws Exception {
        var lot = lot(eventId, "45.00", 10);
        var couponId = coupon(eventId, "VINTE", "PERCENT", "20.00", 1);

        preview(user("USER"), "vinte", lot, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VINTE"))
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.subtotal").value(135.00))
                .andExpect(jsonPath("$.discount").value(27.00))
                .andExpect(jsonPath("$.total").value(108.00));

        assertThat(uses(couponId)).isZero();
    }

    @Test
    void failedAttemptsFromPreviewAndOrdersAreLimitedPerUser() throws Exception {
        var lot = lot(eventId, "50.00", 10);
        coupon(eventId, "VALIDO", "PERCENT", "10.00", null);
        var buyer = user("USER");

        for (int i = 0; i < 5; i++) {
            preview(buyer, "ERRADO" + i, lot, 1).andExpect(status().isUnprocessableEntity());
            createOrder(buyer, "ERRADO" + i, lot, 1).andExpect(status().isUnprocessableEntity());
        }

        preview(buyer, "VALIDO", lot, 1)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, notNullValue()))
                .andExpect(jsonPath("$.type").value(endsWith("/too-many-coupon-attempts")));
        createOrder(buyer, "VALIDO", lot, 1).andExpect(status().isTooManyRequests());
        createOrder(buyer, null, lot, 1).andExpect(status().isCreated());

        preview(user("USER"), "VALIDO", lot, 1).andExpect(status().isOk());
    }

    @Test
    void expiredOrCancelledPendingOrderGivesTheUseBack() {
        var lot = lot(eventId, "60.00", 10);
        var couponId = coupon(eventId, "UNICO", "FIXED", "10.00", 1);
        var first = order(user("USER"), lot, 1, "UNICO");
        assertThat(uses(couponId)).isEqualTo(1);

        var other = user("USER");
        assertThatThrownBy(() -> order(other, lot, 1, "UNICO"))
                .isInstanceOfSatisfying(ProblemException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemType.INVALID_COUPON));

        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), first.id());
        assertThat(orderService.expireOrder(first.id())).isTrue();
        assertThat(uses(couponId)).isZero();

        var second = order(other, lot, 1, "UNICO");
        assertThat(uses(couponId)).isEqualTo(1);
        orderService.cancel(second.id(), other);
        assertThat(uses(couponId)).isZero();
    }

    @Test
    void refundKeepsTheUse() {
        var lot = lot(eventId, "60.00", 10);
        var couponId = coupon(eventId, "REEMBOLSO", "PERCENT", "50.00", 1);
        var buyer = user("USER");
        var order = order(buyer, lot, 1, "REEMBOLSO");
        paymentService.paySimulated(order.id(), buyer);

        orderService.refund(order.id(), buyer);

        assertThat(orderStatus(order.id())).isEqualTo("REFUNDED");
        assertThat(uses(couponId)).isEqualTo(1);
    }

    @Test
    void typeAndValueAreImmutableAfterTheFirstUse() throws Exception {
        var lot = lot(eventId, "60.00", 10);
        var couponId = coupon(eventId, "FIXO", "PERCENT", "10.00", 10);
        var buyer = user("USER");
        var order = order(buyer, lot, 1, "FIXO");
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), order.id());
        orderService.expireOrder(order.id());
        assertThat(uses(couponId)).isZero();

        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":20.00,\"active\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/coupon-already-used")));
        updateCoupon(couponId, "{\"discountType\":\"FIXED\",\"discountValue\":10.00,\"active\":true}")
                .andExpect(status().isConflict());
        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":10.0,\"maxUses\":3,\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(true))
                .andExpect(jsonPath("$.maxUses").value(3));
        mockMvc.perform(delete("/events/{eventId}/coupons/{id}", eventId, couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isConflict());

        assertThatThrownBy(() -> jdbc.update("UPDATE coupons SET discount_value = 99 WHERE id = ?", couponId))
                .hasMessageContaining("cannot change");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM coupons WHERE id = ?", couponId))
                .hasMessageContaining("cannot be deleted");

        var unused = coupon(eventId, "NOVO", "PERCENT", "10.00", null);
        updateCoupon(unused, "{\"discountType\":\"FIXED\",\"discountValue\":15.00,\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FIXED"));
        mockMvc.perform(delete("/events/{eventId}/coupons/{id}", eventId, unused)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT COUNT(*) FROM coupons WHERE id = ?", unused)).isZero();
    }

    @Test
    void maxUsesCannotDropBelowTheUsesAlreadyMade() throws Exception {
        var lot = lot(eventId, "60.00", 10);
        var couponId = coupon(eventId, "LIMITE", "PERCENT", "10.00", 5);
        order(user("USER"), lot, 1, "LIMITE");
        order(user("USER"), lot, 1, "LIMITE");

        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":10.00,\"maxUses\":1,\"active\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-coupon-settings")));
        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":10.00,\"maxUses\":2,\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usesCount").value(2));
    }

    @Test
    void ownerManagesCouponsAndEveryChangeIsAudited() throws Exception {
        var created = mockMvc.perform(post("/events/{eventId}/coupons", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" black-friday \",\"discountType\":\"PERCENT\",\"discountValue\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BLACK-FRIDAY"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.usesCount").value(0))
                .andReturn().getResponse().getContentAsString();
        var couponId = UUID.fromString(created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        mockMvc.perform(post("/events/{eventId}/coupons", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Black-Friday\",\"discountType\":\"FIXED\",\"discountValue\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/coupon-code-already-exists")));
        mockMvc.perform(post("/events/{eventId}/coupons", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEMAIS\",\"discountType\":\"PERCENT\",\"discountValue\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-coupon-settings")));
        mockMvc.perform(post("/events/{eventId}/coupons", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user("ORGANIZER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"INTRUSO\",\"discountType\":\"PERCENT\",\"discountValue\":10}"))
                .andExpect(status().isForbidden());

        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":30,\"maxUses\":50,\"active\":true}")
                .andExpect(status().isOk());
        updateCoupon(couponId, "{\"discountType\":\"PERCENT\",\"discountValue\":30,\"maxUses\":50,\"active\":false}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/events/{eventId}/coupons", eventId).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BLACK-FRIDAY"))
                .andExpect(jsonPath("$[0].discountValue").value(30))
                .andExpect(jsonPath("$[0].active").value(false));

        mockMvc.perform(delete("/events/{eventId}/coupons/{id}", eventId, couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isNoContent());

        var actions = jdbc.queryForList(
                "SELECT action FROM audit_log WHERE target_type = 'COUPON' AND target_id = ? ORDER BY created_at, id",
                String.class, couponId);
        assertThat(actions).containsExactlyInAnyOrder(
                "COUPON_CREATED", "COUPON_UPDATED", "COUPON_DEACTIVATED", "COUPON_DELETED");
        var update = jdbc.queryForObject(
                "SELECT details::text FROM audit_log WHERE target_id = ? AND action = 'COUPON_UPDATED'",
                String.class, couponId);
        assertThat(update).contains("discountValue").contains("maxUses").doesNotContain("\"active\"");
        assertThat(jdbc.queryForObject(
                "SELECT actor_id FROM audit_log WHERE target_id = ? AND action = 'COUPON_CREATED'",
                UUID.class, couponId)).isEqualTo(organizer.getId());
    }

    @Test
    void dashboardShowsDiscountsAndUsesPerCoupon() throws Exception {
        var lot = lot(eventId, "100.00", 10);
        var couponId = coupon(eventId, "PAINEL", "PERCENT", "10.00", 10);
        var buyer = user("USER");
        var paid = order(buyer, lot, 2, "PAINEL");
        paymentService.paySimulated(paid.id(), buyer);
        order(user("USER"), lot, 1, "PAINEL");

        mockMvc.perform(get("/events/{id}/dashboard", eventId).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value(200.00))
                .andExpect(jsonPath("$.totals.discounts").value(20.00))
                .andExpect(jsonPath("$.totals.platformFee").value(9.00))
                .andExpect(jsonPath("$.totals.netRevenue").value(171.00))
                .andExpect(jsonPath("$.coupons[0].id").value(couponId.toString()))
                .andExpect(jsonPath("$.coupons[0].code").value("PAINEL"))
                .andExpect(jsonPath("$.coupons[0].uses").value(2))
                .andExpect(jsonPath("$.coupons[0].maxUses").value(10))
                .andExpect(jsonPath("$.coupons[0].paidOrders").value(1))
                .andExpect(jsonPath("$.coupons[0].discountTotal").value(20.00));

        mockMvc.perform(get("/events/{id}/orders", eventId).param("status", "PAID")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subtotal").value(200.00))
                .andExpect(jsonPath("$.content[0].discount").value(20.00))
                .andExpect(jsonPath("$.content[0].total").value(180.00))
                .andExpect(jsonPath("$.content[0].couponCode").value("PAINEL"));

        mockMvc.perform(get("/organizer/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.discounts").value(20.00))
                .andExpect(jsonPath("$.events.content[0].discounts").value(20.00));
    }

    private ResultActions preview(User buyer, String code, UUID lot, int quantity) throws Exception {
        return mockMvc.perform(post("/events/{eventId}/coupons/preview", eventId)
                .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":"
                        + quantity + "}]}"));
    }

    private ResultActions createOrder(User buyer, String code, UUID lot, int quantity) throws Exception {
        var coupon = code == null ? "" : ",\"couponCode\":\"" + code + "\"";
        return mockMvc.perform(post("/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":" + quantity + "}]" + coupon + "}"));
    }

    private ResultActions updateCoupon(UUID couponId, String body) throws Exception {
        return mockMvc.perform(put("/events/{eventId}/coupons/{id}", eventId, couponId)
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
