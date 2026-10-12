package com.lutfy.ticketfy.user.admin;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.payout.withdrawal.PayoutProcessingService;
import com.lutfy.ticketfy.privacy.AccountDeletionRequestDTO;
import com.lutfy.ticketfy.privacy.AccountDeletionService;
import com.lutfy.ticketfy.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSuspensionIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final String COOKIE = "__Host-ticketfy_rt";

    @Autowired
    private AccountSuspensionService suspensions;

    @Autowired
    private AccountDeletionService deletions;

    @Autowired
    private PayoutProcessingService processing;

    @Test
    void suspensionRevokesSessionsAndRefusesLoginAndRefresh() throws Exception {
        var admin = user("ADMIN");
        var token = bearer(buyer);
        var cookie = cookieOf(login(buyer, PASSWORD).andExpect(status().isOk()));
        me(token).andExpect(status().isOk());

        suspend(admin, buyer, "  Fraude em análise  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspensionReason").value("Fraude em análise"))
                .andExpect(jsonPath("$.suspendedBy").value(admin.getId().toString()));

        me(token).andExpect(status().isUnauthorized());
        login(buyer, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "account-suspended"))
                .andExpect(jsonPath("$.detail").value("This account is suspended. Contact support."));
        login(buyer, "senha-errada-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-credentials"));
        var refreshed = mockMvc.perform(post("/auth/refresh").with(fromFrontend()).cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "account-suspended"))
                .andReturn();
        assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions
                 WHERE user_id = ? AND (revoked_at IS NULL OR revoked_reason <> 'ACCOUNT_SUSPENDED')
                """, Integer.class, buyer.getId())).isZero();
    }

    @Test
    void reactivationRestoresAccess() throws Exception {
        var admin = user("ADMIN");
        suspend(admin, buyer, "Revisão").andExpect(status().isOk());
        login(buyer, PASSWORD).andExpect(status().isForbidden());

        reactivate(admin, buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.suspensionReason").doesNotExist());

        var token = body(login(buyer, PASSWORD).andExpect(status().isOk())).get("token").asString();
        me("Bearer " + token).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT suspended_at IS NULL AND suspension_reason IS NULL FROM users WHERE id = ?",
                Boolean.class, buyer.getId())).isTrue();
    }

    @Test
    void adminsCannotBeSuspendedAndOnlyAdminsManageSuspensions() throws Exception {
        var admin = user("ADMIN");
        var otherAdmin = user("ADMIN");

        suspend(admin, admin, "Teste")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "admin-account-suspension"));
        suspend(admin, otherAdmin, "Teste")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "admin-account-suspension"));
        suspend(admin, buyer, " ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/users/{id}/suspension", UUID.randomUUID())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "user-not-found"));
        reactivate(admin, buyer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-suspension-state"));
        suspend(admin, buyer, "Primeira").andExpect(status().isOk());
        suspend(admin, buyer, "Segunda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-suspension-state"));

        for (var outsider : List.of(user("USER"), user("ORGANIZER"))) {
            find(outsider, organizer.getEmail()).andExpect(status().isForbidden());
            suspend(outsider, organizer, "Teste").andExpect(status().isForbidden());
            reactivate(outsider, buyer).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT suspended_at IS NULL FROM users WHERE id = ?", Boolean.class,
                organizer.getId())).isTrue();
    }

    @Test
    void adminFindsAnyUserByEmail() throws Exception {
        var admin = user("ADMIN");
        find(admin, buyer.getEmail())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(buyer.getId().toString()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.payoutsBlocked").value(false));
        find(admin, "ninguem-" + UUID.randomUUID() + "@test.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "user-not-found"));
    }

    @Test
    void pendingOrdersAreCancelledAndPaidTicketsStayValid() throws Exception {
        var admin = user("ADMIN");
        var eventId = insertEvent(organizer.getId(), Instant.now().plus(Duration.ofDays(20)),
                Instant.now().plus(Duration.ofDays(20)).plus(Duration.ofHours(3)));
        var lot = insertTicketType(eventId, "50.00");
        var couponId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO coupons (id, event_id, code, discount_type, discount_value, max_uses)
                VALUES (?, ?, 'SUSPENSO10', 'PERCENT', 10, 5)
                """, couponId, eventId);
        var paid = orderService.create(order(lot, 1, null), null, buyer).id();
        paymentService.paySimulated(paid, buyer);
        var pending = orderService.create(order(lot, 2, "suspenso10"), null, buyer).id();
        assertThat(quantitySold(lot)).isEqualTo(3);
        assertThat(couponUses(couponId)).isEqualTo(1);

        suspend(admin, buyer, "Chargeback").andExpect(status().isOk());

        assertThat(orderStatus(pending)).isEqualTo("CANCELLED");
        assertThat(orderStatus(paid)).isEqualTo("PAID");
        assertThat(quantitySold(lot)).isEqualTo(1);
        assertThat(couponUses(couponId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE order_id = ? AND status = 'VALID'",
                Integer.class, paid)).isEqualTo(1);
        var details = auditDetails("ACCOUNT_SUSPENDED", buyer.getId());
        assertThat(details).containsEntry("cancelledPendingOrders", 1);
    }

    @Test
    void suspendedOrganizerHasPayoutsHeldAndSalesPaused() throws Exception {
        var admin = user("ADMIN");
        releasedSales(organizer, "100.00");
        registerAccountWithPayoutHistory(organizer);
        var payoutId = body(requestPayout(organizer, "30.00", PASSWORD, null)).get("id").asString();
        var startsAt = clock.instant().plus(Duration.ofDays(10));
        var eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        var name = "Suspenso " + UUID.randomUUID();
        jdbc.update("UPDATE events SET name = ?, featured = true WHERE id = ?", name, eventId);
        var lot = insertTicketType(eventId, "40.00");
        var otherBuyer = user("USER");

        suspend(admin, organizer, "Evento sob investigação").andExpect(status().isOk());

        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("REQUESTED");
        createOrder(otherBuyer, lot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "sales-closed"));
        assertThat(quantitySold(lot)).isZero();
        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesClosed").value(true));
        mockMvc.perform(get("/events").param("q", name))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/events").param("q", name).param("featured", "true"))
                .andExpect(jsonPath("$.content.length()").value(0));

        reactivate(admin, organizer).andExpect(status().isOk());

        processing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        createOrder(otherBuyer, lot).andExpect(status().isCreated());
        mockMvc.perform(get("/events/{id}", eventId)).andExpect(jsonPath("$.salesClosed").value(false));
        mockMvc.perform(get("/events").param("q", name))
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()));
    }

    @Test
    void suspensionAndReactivationAreAudited() throws Exception {
        var admin = user("ADMIN");
        suspend(admin, buyer, "Uso de cartão roubado").andExpect(status().isOk());
        reactivate(admin, buyer).andExpect(status().isOk());

        var suspended = auditDetails("ACCOUNT_SUSPENDED", buyer.getId());
        assertThat(suspended).containsEntry("reason", "Uso de cartão roubado").containsEntry("role", "USER");
        var reactivated = auditDetails("ACCOUNT_REACTIVATED", buyer.getId());
        assertThat(reactivated).containsEntry("previousReason", "Uso de cartão roubado");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                 WHERE target_id = ? AND action IN ('ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED') AND actor_id = ?
                """, Integer.class, buyer.getId(), admin.getId())).isEqualTo(2);
    }

    @Test
    void suspendedAccountCannotBeDeleted() {
        var admin = user("ADMIN");
        suspensions.suspend(buyer.getId(), "Investigação", admin);

        assertThatThrownBy(() -> deletions.delete(buyer, new AccountDeletionRequestDTO(PASSWORD, "EXCLUIR")))
                .isInstanceOfSatisfying(ProblemException.class,
                        problem -> assertThat(problem.getType()).isEqualTo(ProblemType.ACCOUNT_SUSPENDED));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM users WHERE id = ?", Boolean.class,
                buyer.getId())).isTrue();
    }

    @Test
    void suspensionRacingWithOrderCreationKeepsInvariants() throws Exception {
        var admin = user("ADMIN");
        var eventId = insertEvent(organizer.getId(), Instant.now().plus(Duration.ofDays(20)),
                Instant.now().plus(Duration.ofDays(20)).plus(Duration.ofHours(3)));
        var lot = insertTicketType(eventId, "25.00");
        for (int round = 0; round < 6; round++) {
            var target = user("USER");
            var reason = "Disputa " + round;
            var outcomes = race(
                    () -> orderService.create(order(lot, 2, null), null, target),
                    () -> suspensions.suspend(target.getId(), reason, admin));

            assertThat(outcomes.get(1)).isInstanceOf(AdminUserDTO.class);
            if (outcomes.get(0) instanceof ProblemException problem) {
                assertThat(problem.getType()).isEqualTo(ProblemType.SESSION_EXPIRED);
            } else {
                assertThat(outcomes.get(0)).isNotInstanceOf(Throwable.class);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = 'PENDING'",
                    Integer.class, target.getId())).isZero();
            assertThat(quantitySold(lot)).isEqualTo(jdbc.queryForObject("""
                    SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                      JOIN orders o ON o.id = oi.order_id
                     WHERE oi.ticket_type_id = ? AND o.status IN ('PENDING', 'PAID')
                    """, Integer.class, lot));
        }
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Object>>();
            for (var action : List.of(first, second)) {
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

    private OrderCreationDTO order(UUID lot, int quantity, String coupon) {
        return new OrderCreationDTO(List.of(new OrderItemRequestDTO(lot, quantity)), coupon);
    }

    private ResultActions suspend(User admin, User target, String reason) throws Exception {
        return mockMvc.perform(post("/admin/users/{id}/suspension", target.getId())
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions reactivate(User admin, User target) throws Exception {
        return mockMvc.perform(delete("/admin/users/{id}/suspension", target.getId())
                .header("Authorization", bearer(admin)));
    }

    private ResultActions find(User admin, String email) throws Exception {
        return mockMvc.perform(get("/admin/users").param("email", email).header("Authorization", bearer(admin)));
    }

    private ResultActions login(User target, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(fromFrontend())
                .with(request -> {
                    request.setRemoteAddr("suspension-test-" + UUID.randomUUID());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", target.getEmail(), "password", password))));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/users/me").header("Authorization", token));
    }

    private ResultActions createOrder(User buyer, UUID lot) throws Exception {
        return mockMvc.perform(post("/orders")
                .header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":1}]}"));
    }

    private static String cookieOf(ResultActions result) {
        var header = result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private int quantitySold(UUID lot) {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, lot);
    }

    private int couponUses(UUID couponId) {
        return jdbc.queryForObject("SELECT uses_count FROM coupons WHERE id = ?", Integer.class, couponId);
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String payoutStatus(String payoutId) {
        return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?::uuid", String.class, payoutId);
    }

    private Map<String, Object> auditDetails(String action, UUID targetId) throws Exception {
        var json = jdbc.queryForObject("""
                SELECT details::text FROM audit_log WHERE action = ? AND target_id = ?
                 ORDER BY created_at DESC LIMIT 1
                """, String.class, action, targetId);
        return JSON.readValue(json, Map.class);
    }
}
