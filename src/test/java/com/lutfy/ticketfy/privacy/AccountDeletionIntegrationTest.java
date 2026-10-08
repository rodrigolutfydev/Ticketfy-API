package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.payout.admin.AdminPayoutService;
import com.lutfy.ticketfy.payout.withdrawal.PayoutProcessingService;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionIntegrationTest extends PrivacyTestBase {

    @Autowired
    private AdminPayoutService adminPayoutService;

    @Autowired
    private PayoutProcessingService payoutProcessing;

    @Test
    void requiresTypedConfirmation() throws Exception {
        var user = person("USER", "Ana");

        deleteAccount(user, PASSWORD, "excluir")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")))
                .andExpect(jsonPath("$.errors[0].field").value("confirmation"));

        assertNotDeleted(user);
    }

    @Test
    void requiresPassword() throws Exception {
        var user = person("USER", "Bruno");

        deleteAccount(user, "senha-errada", "EXCLUIR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-password")));

        assertNotDeleted(user);
    }

    @Test
    void refusesAdmin() throws Exception {
        var admin = person("ADMIN", "Alice");

        expectRefusal(deleteAccount(admin, PASSWORD, "EXCLUIR"), "admin-account-deletion");
        assertNotDeleted(admin);
    }

    @Test
    void refusesOrganizerWithBlockedPayouts() throws Exception {
        var organizer = person("ORGANIZER", "Bela");
        var admin = person("ADMIN", "Carla");
        jdbc.update("INSERT INTO payout_blocks (organizer_id, reason, blocked_by, blocked_at) VALUES (?, ?, ?, ?)",
                organizer.getId(), "Investigação", admin.getId(), Timestamp.from(Instant.now()));

        expectRefusal(deleteAccount(organizer, PASSWORD, "EXCLUIR"), "account-has-payout-block");
        assertNotDeleted(organizer);
    }

    @Test
    void refusesPayoutInProgress() throws Exception {
        var organizer = person("ORGANIZER", "Caua");
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        requestPayout(organizer, "95.00", PASSWORD, null).andExpect(status().isCreated());
        assertThat(balance(organizer).get("total").decimalValue()).isEqualByComparingTo("0.00");

        expectRefusal(deleteAccount(organizer, PASSWORD, "EXCLUIR"), "account-has-payout-in-progress");
        assertNotDeleted(organizer);
        assertThat(payoutAccounts(organizer)).isEqualTo(1);
    }

    @Test
    void refusesNonZeroBalance() throws Exception {
        var organizer = person("ORGANIZER", "Davi");
        releasedSales(organizer, "100.00");

        expectRefusal(deleteAccount(organizer, PASSWORD, "EXCLUIR"), "account-has-balance")
                .andExpect(jsonPath("$.available").value(95.00))
                .andExpect(jsonPath("$.pending").value(0.00))
                .andExpect(jsonPath("$.held").value(0.00));
        assertNotDeleted(organizer);
    }

    @Test
    void refusesEventWithActiveSales() throws Exception {
        var organizer = person("ORGANIZER", "Elis");
        var eventId = upcomingEvent(organizer);
        pendingOrder(buyer, insertTicketType(eventId, "40.00"), 1);
        var emptyEvent = upcomingEvent(organizer);
        insertTicketType(emptyEvent, "10.00");

        expectRefusal(deleteAccount(organizer, PASSWORD, "EXCLUIR"), "account-has-active-events")
                .andExpect(jsonPath("$.eventIds.length()").value(1))
                .andExpect(jsonPath("$.eventIds[0]").value(eventId.toString()));
        assertNotDeleted(organizer);
        assertThat(eventActive(emptyEvent)).isTrue();
    }

    @Test
    void refusesValidTicketsForUpcomingEvents() throws Exception {
        var organizer = person("ORGANIZER", "Fabio");
        var holder = person("USER", "Gabi");
        paidOrder(holder, insertTicketType(upcomingEvent(organizer), "30.00"), 2);
        paidOrder(holder, insertTicketType(pastEvent(organizer), "30.00"), 1);

        expectRefusal(deleteAccount(holder, PASSWORD, "EXCLUIR"), "account-has-upcoming-tickets")
                .andExpect(jsonPath("$.ticketCount").value(2));
        assertNotDeleted(holder);
    }

    @Test
    void anonymizesBuyerRevokesSessionsAndKeepsRecords() throws Exception {
        var organizer = person("ORGANIZER", "Heitor");
        var deleted = person("USER", "Iris");
        var pastLot = insertTicketType(pastEvent(organizer), "60.00");
        var upcomingLot = insertTicketType(upcomingEvent(organizer), "25.00");
        var paidOrderId = paidOrder(deleted, pastLot, 2);
        var pendingOrderId = pendingOrder(deleted, upcomingLot, 3);
        var originalName = deleted.getName();
        var originalEmail = deleted.getEmail();
        var firstToken = bearer(deleted);
        var secondToken = bearer(deleted);
        mockMvc.perform(get("/users/me").header("Authorization", firstToken)).andExpect(status().isOk());

        var ordersBefore = orderTotals(deleted);
        var ledgerBefore = ledger(organizer);
        var auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
        var auditRowsBefore = auditRowsExceptDeletionOf(deleted);

        deleteAccount(firstToken, PASSWORD, "EXCLUIR")
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(get("/users/me").header("Authorization", firstToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me").header("Authorization", secondToken)).andExpect(status().isUnauthorized());

        var row = jdbc.queryForMap("SELECT name, email, avatar_url, password, deleted_at FROM users WHERE id = ?",
                deleted.getId());
        assertThat(row.get("name")).isEqualTo(User.DELETED_NAME);
        assertThat(row.get("email")).isEqualTo("deleted-" + deleted.getId() + "@deleted.ticketfy.invalid");
        assertThat(row.get("avatar_url")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(passwordEncoder.matches(PASSWORD, (String) row.get("password"))).isFalse();
        assertThat(jdbc.queryForList("SELECT DISTINCT revoked_reason FROM auth_sessions WHERE user_id = ?",
                String.class, deleted.getId())).containsExactly("ACCOUNT_DELETED");

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, pendingOrderId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class,
                upcomingLot)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, paidOrderId))
                .isEqualTo("PAID");
        assertThat(orderTotals(deleted)).isEqualTo(ordersBefore);
        assertThat(ledger(organizer)).isEqualTo(ledgerBefore);
        assertThat(auditRowsExceptDeletionOf(deleted)).isEqualTo(auditRowsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isEqualTo(auditBefore + 1);

        var audit = jdbc.queryForMap("""
                SELECT actor_id, target_type, details::text AS details FROM audit_log
                 WHERE action = 'ACCOUNT_DELETED' AND target_id = ?
                """, deleted.getId());
        assertThat(audit.get("actor_id")).isEqualTo(deleted.getId());
        assertThat(audit.get("target_type")).isEqualTo("USER");
        var details = JSON.readTree((String) audit.get("details"));
        assertThat(details.get("revokedSessions").asInt()).isEqualTo(2);
        assertThat(details.get("cancelledPendingOrders").asInt()).isEqualTo(1);
        assertThat(details.get("payoutAccountRemoved").asBoolean()).isFalse();

        assertThat(columnsContaining(originalName, originalEmail)).isEmpty();

        mockMvc.perform(post("/auth/login").with(fromFrontend())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", originalEmail, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        var registered = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "name", "Nova Conta", "email", originalEmail, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(originalEmail));
        assertThat(body(registered).get("id").asString()).isNotEqualTo(deleted.getId().toString());

        mockMvc.perform(get("/events/" + organizerEventOf(upcomingLot))).andExpect(status().isOk());
    }

    @Test
    void anonymizesOrganizerAndKeepsPayoutDestination() throws Exception {
        var organizer = person("ORGANIZER", "Joana");
        var admin = person("ADMIN", "Kleber");
        var buyerOrder = paidOrder(buyer, insertTicketType(pastEvent(organizer), "100.00"), 1);
        var emptyUpcoming = upcomingEvent(organizer);
        insertTicketType(emptyUpcoming, "15.00");
        var cancelledEvent = upcomingEvent(organizer);
        jdbc.update("UPDATE events SET cancelled_at = NOW() WHERE id = ?", cancelledEvent);
        registerAccount(organizer);
        travelTo(Instant.now().plus(java.time.Duration.ofDays(5)));
        var payoutId = UUID.fromString(body(requestPayout(organizer, "95.00", PASSWORD, null)
                .andExpect(status().isCreated())).get("id").asString());
        adminPayoutService.approve(payoutId, admin);
        payoutProcessing.processAll();
        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        var destinationBefore = jdbc.queryForMap(
                "SELECT document, pix_key, holder_name FROM payouts WHERE id = ?", payoutId);
        var ledgerBefore = ledger(organizer);
        var originalName = organizer.getName();
        var originalEmail = organizer.getEmail();

        deleteAccount(organizer, PASSWORD, "EXCLUIR").andExpect(status().isNoContent());

        assertThat(payoutAccounts(organizer)).isZero();
        assertThat(payoutStatus(payoutId)).isEqualTo("PAID");
        assertThat(jdbc.queryForMap("SELECT document, pix_key, holder_name FROM payouts WHERE id = ?", payoutId))
                .isEqualTo(destinationBefore);
        assertThat(cipher.decrypt((String) destinationBefore.get("document"))).isEqualTo(CPF);
        assertThat(ledger(organizer)).isEqualTo(ledgerBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE organizer_id = ? AND active",
                Integer.class, organizer.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE organizer_id = ?",
                Integer.class, organizer.getId())).isEqualTo(3);
        mockMvc.perform(get("/events/" + emptyUpcoming)).andExpect(status().isNotFound());

        mockMvc.perform(get("/orders/" + buyerOrder).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00));

        var details = JSON.readTree(jdbc.queryForObject(
                "SELECT details::text FROM audit_log WHERE action = 'ACCOUNT_DELETED' AND target_id = ?",
                String.class, organizer.getId()));
        assertThat(details.get("deactivatedEvents").asInt()).isEqualTo(3);
        assertThat(details.get("payoutAccountRemoved").asBoolean()).isTrue();

        assertThat(columnsContaining(originalName, originalEmail)).isEmpty();
    }

    @Test
    void rejectsReservedEmailDomainOnRegistration() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("name", "Intruso",
                                "email", "deleted-" + UUID.randomUUID() + "@Deleted.Ticketfy.Invalid",
                                "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/email-not-allowed")));
    }

    private ResultActions expectRefusal(ResultActions result, String type) throws Exception {
        return result.andExpect(status().isConflict()).andExpect(jsonPath("$.type").value(endsWith("/" + type)));
    }

    private void assertNotDeleted(User user) {
        var row = jdbc.queryForMap("SELECT name, email, deleted_at FROM users WHERE id = ?", user.getId());
        assertThat(row.get("name")).isEqualTo(user.getName());
        assertThat(row.get("email")).isEqualTo(user.getEmail());
        assertThat(row.get("deleted_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_DELETED' AND target_id = ?",
                Integer.class, user.getId())).isZero();
    }

    private int payoutAccounts(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payout_accounts WHERE organizer_id = ?", Integer.class,
                user.getId());
    }

    private String payoutStatus(UUID payoutId) {
        return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?", String.class, payoutId);
    }

    private boolean eventActive(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM events WHERE id = ?", Boolean.class, eventId));
    }

    private UUID organizerEventOf(UUID ticketTypeId) {
        return jdbc.queryForObject("SELECT event_id FROM ticket_types WHERE id = ?", UUID.class, ticketTypeId);
    }

    private Map<String, Object> orderTotals(User user) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS orders, COALESCE(SUM(total_amount), 0) AS total,
                       COALESCE(SUM(subtotal_amount), 0) AS subtotal
                  FROM orders WHERE user_id = ?
                """, user.getId());
    }

    private Map<String, Object> ledger(User organizer) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS entries, COALESCE(SUM(amount), 0) AS total
                  FROM organizer_ledger_entries WHERE organizer_id = ?
                """, organizer.getId());
    }

    private java.util.List<Map<String, Object>> auditRowsExceptDeletionOf(User user) {
        return jdbc.queryForList("""
                SELECT id, actor_id, action, target_type, target_id, details::text AS details, created_at
                  FROM audit_log
                 WHERE NOT (action = 'ACCOUNT_DELETED' AND target_id = ?)
                 ORDER BY id
                """, user.getId());
    }
}
