package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.infra.logging.JobRun;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditIntegrationTest extends PayoutTestBase {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private PayoutProcessingService processing;

    @Test
    void payoutAccountChangesAreAuditedWithoutSensitiveData() throws Exception {
        var created = requestId();
        account(organizer, "EMAIL", "maria.silva@example.com", PASSWORD, created).andExpect(status().isOk());
        var renamed = requestId();
        account(organizer, "EMAIL", "maria.silva@example.com", PASSWORD, renamed).andExpect(status().isOk());
        var keyChanged = requestId();
        account(organizer, "CPF", CPF, PASSWORD, keyChanged).andExpect(status().isOk());

        var rows = audit(organizer.getId());
        assertThat(rows).extracting(row -> row.get("action")).containsOnly("PAYOUT_ACCOUNT_SAVED");
        assertThat(rows).hasSize(3);
        assertRow(only(rows, created), "PAYOUT_ACCOUNT_SAVED", organizer, created);
        assertThat(details(only(rows, created))).containsEntry("created", true).containsEntry("keyChanged", false);
        assertThat(details(only(rows, renamed))).containsEntry("created", false).containsEntry("keyChanged", false);
        assertThat(details(only(rows, keyChanged))).containsEntry("keyChanged", true).containsEntry("pixKeyType", "CPF");
        assertNoSensitiveData();
    }

    @Test
    void payoutLifecycleIsAudited() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var admin = user("ADMIN");

        var requested = requestId();
        var first = body(mockMvc.perform(payoutRequest(organizer, "40.00", requested))
                .andExpect(status().isCreated())).get("id").asString();
        var approved = requestId();
        mockMvc.perform(withId(post("/admin/payouts/" + first + "/approve"), admin, approved)).andExpect(status().isOk());
        JobRun.run(processing::processAll);

        var firstRows = audit(UUID.fromString(first));
        assertThat(firstRows).extracting(row -> row.get("action"))
                .containsExactly("PAYOUT_REQUESTED", "PAYOUT_SENT_TO_REVIEW", "PAYOUT_APPROVED", "PAYOUT_PAID");
        assertRow(firstRows.get(0), "PAYOUT_REQUESTED", organizer, requested);
        assertRow(firstRows.get(1), "PAYOUT_SENT_TO_REVIEW", organizer, requested);
        assertThat(details(firstRows.get(1)).get("reasons")).isEqualTo(List.of("FIRST_PAYOUT"));
        assertRow(firstRows.get(2), "PAYOUT_APPROVED", admin, approved);
        var paid = firstRows.get(3);
        assertThat(paid.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(paid.get("actor_id")).isNull();
        assertThat((String) paid.get("correlation_id")).isNotBlank();

        var second = body(mockMvc.perform(payoutRequest(organizer, "20.00", requestId()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))).get("id").asString();
        var cancelled = requestId();
        mockMvc.perform(withId(post("/organizer/payouts/" + second + "/cancel"), organizer, cancelled))
                .andExpect(status().isOk());
        assertThat(audit(UUID.fromString(second))).extracting(row -> row.get("action"))
                .containsExactly("PAYOUT_REQUESTED", "PAYOUT_CANCELLED");
        assertRow(audit(UUID.fromString(second)).get(1), "PAYOUT_CANCELLED", organizer, cancelled);

        var third = body(mockMvc.perform(payoutRequest(organizer, "20.00", requestId()))).get("id").asString();
        doReturn(new PayoutGateway.TransferResult(PayoutGateway.TransferStatus.FAILED, null, "Conta encerrada"))
                .when(payoutGateway).transfer(any());
        JobRun.run(processing::processAll);
        var failed = audit(UUID.fromString(third)).get(1);
        assertThat(failed.get("action")).isEqualTo("PAYOUT_FAILED");
        assertThat(failed.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(details(failed)).containsEntry("failureReason", "Conta encerrada");
        assertNoSensitiveData();
    }

    @Test
    void rejectionAndBlocksAreAudited() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var admin = user("ADMIN");
        var payout = body(mockMvc.perform(payoutRequest(organizer, "30.00", requestId()))).get("id").asString();

        var rejected = requestId();
        mockMvc.perform(withId(post("/admin/payouts/" + payout + "/reject"), admin, rejected)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Titular divergente\"}"))
                .andExpect(status().isOk());
        var blocked = requestId();
        mockMvc.perform(withId(post("/admin/organizers/" + organizer.getId() + "/payout-block"), admin, blocked)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Chargeback\"}"))
                .andExpect(status().isOk());
        var unblocked = requestId();
        mockMvc.perform(withId(delete("/admin/organizers/" + organizer.getId() + "/payout-block"), admin, unblocked))
                .andExpect(status().isOk());

        var rejection = audit(UUID.fromString(payout)).stream()
                .filter(row -> row.get("action").equals("PAYOUT_REJECTED")).toList();
        assertThat(rejection).hasSize(1);
        assertRow(rejection.get(0), "PAYOUT_REJECTED", admin, rejected);
        assertThat(details(rejection.get(0))).containsEntry("reason", "Titular divergente");
        var organizerRows = audit(organizer.getId()).stream()
                .filter(row -> row.get("target_type").equals("ORGANIZER")).toList();
        assertThat(organizerRows).hasSize(2);
        assertRow(organizerRows.get(0), "PAYOUTS_BLOCKED", admin, blocked);
        assertThat(details(organizerRows.get(0))).containsEntry("reason", "Chargeback");
        assertRow(organizerRows.get(1), "PAYOUTS_UNBLOCKED", admin, unblocked);
    }

    @Test
    void eventAndLotChangesAreAudited() throws Exception {
        var admin = user("ADMIN");
        var startsAt = Instant.now().plus(Duration.ofDays(20));
        var eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        var ticketTypeId = insertTicketType(eventId, "80.00");

        var featured = requestId();
        mockMvc.perform(withId(patch("/events/" + eventId + "/featured"), admin, featured)
                .contentType(MediaType.APPLICATION_JSON).content("{\"featured\":true}")).andExpect(status().isOk());
        mockMvc.perform(withId(patch("/events/" + eventId + "/featured"), admin, requestId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"featured\":true}")).andExpect(status().isOk());
        var unfeatured = requestId();
        mockMvc.perform(withId(patch("/events/" + eventId + "/featured"), admin, unfeatured)
                .contentType(MediaType.APPLICATION_JSON).content("{\"featured\":false}")).andExpect(status().isOk());

        var priced = requestId();
        mockMvc.perform(withId(patch("/events/" + eventId + "/ticket-types/" + ticketTypeId), organizer, priced)
                .contentType(MediaType.APPLICATION_JSON).content("{\"price\":95.50}")).andExpect(status().isOk());
        mockMvc.perform(withId(patch("/events/" + eventId + "/ticket-types/" + ticketTypeId), organizer, requestId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pista\",\"price\":95.5}")).andExpect(status().isOk());

        var cancelled = requestId();
        mockMvc.perform(withId(post("/events/" + eventId + "/cancel"), organizer, cancelled)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Chuva\"}")).andExpect(status().isOk());

        var eventRows = audit(eventId);
        assertThat(eventRows).extracting(row -> row.get("action"))
                .containsExactly("EVENT_FEATURED", "EVENT_UNFEATURED", "EVENT_CANCELLED");
        assertRow(eventRows.get(0), "EVENT_FEATURED", admin, featured);
        assertRow(eventRows.get(1), "EVENT_UNFEATURED", admin, unfeatured);
        assertRow(eventRows.get(2), "EVENT_CANCELLED", organizer, cancelled);
        assertThat(details(eventRows.get(2))).containsEntry("reason", "Chuva");

        var lotRows = audit(ticketTypeId);
        assertThat(lotRows).hasSize(1);
        assertRow(lotRows.get(0), "TICKET_TYPE_PRICE_CHANGED", organizer, priced);
        assertThat(new BigDecimal(details(lotRows.get(0)).get("previousPrice").toString())).isEqualByComparingTo("80.00");
        assertThat(new BigDecimal(details(lotRows.get(0)).get("newPrice").toString())).isEqualByComparingTo("95.50");
    }

    @Test
    void failedActionsLeaveNoAuditRecord() throws Exception {
        releasedSales(organizer, "100.00");
        account(organizer, "EMAIL", "maria.silva@example.com", "wrong-password", requestId()).andExpect(status().isForbidden());
        registerAccount(organizer);
        var before = count();

        mockMvc.perform(payoutRequest(organizer, "500.00", requestId())).andExpect(status().isConflict());
        var startsAt = Instant.now().plus(Duration.ofDays(20));
        var eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        var ticketTypeId = insertTicketType(eventId, "80.00");
        jdbc.update("UPDATE ticket_types SET quantity_sold = 10 WHERE id = ?", ticketTypeId);
        mockMvc.perform(withId(patch("/events/" + eventId + "/ticket-types/" + ticketTypeId), organizer, requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":120.00,\"quantityTotal\":5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withId(post("/admin/payouts/" + UUID.randomUUID() + "/approve"), user("ADMIN"), requestId()))
                .andExpect(status().isNotFound());

        assertThat(count()).isEqualTo(before);
        assertThat(audit(ticketTypeId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT price FROM ticket_types WHERE id = ?", BigDecimal.class, ticketTypeId))
                .isEqualByComparingTo("80.00");
    }

    @Test
    void auditLogIsAppendOnly() throws Exception {
        registerAccount(organizer);
        var id = audit(organizer.getId()).get(0).get("id");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'X' WHERE id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", id))
                .hasMessageContaining("append-only");
        assertThat(audit(organizer.getId())).hasSize(1);
    }

    @Test
    void adminQueriesTheAuditNewestFirstWithFilters() throws Exception {
        var admin = user("ADMIN");
        registerAccount(organizer);
        account(organizer, "CPF", CPF, PASSWORD, requestId()).andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit")
                        .param("targetType", "PAYOUT_ACCOUNT")
                        .param("targetId", organizer.getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].details.keyChanged").value(true))
                .andExpect(jsonPath("$.content[1].details.keyChanged").value(false))
                .andExpect(jsonPath("$.content[0].actorEmail").value(organizer.getEmail()));
        mockMvc.perform(get("/admin/audit").param("actorId", organizer.getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mockMvc.perform(get("/admin/audit").param("actorId", organizer.getId().toString())
                        .param("from", "2000-01-01").param("to", "2000-01-31")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/admin/audit").param("from", "2026-02-01").param("to", "2026-01-01")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions account(User user, String keyType, String key,
                                  String password, String requestId) throws Exception {
        return mockMvc.perform(withId(put("/organizer/payout-account"), user, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("documentType", "CPF", "document", CPF,
                        "holderName", "Maria da Silva", "pixKeyType", keyType, "pixKey", key, "password", password))));
    }

    private MockHttpServletRequestBuilder payoutRequest(User user, String amount, String requestId) {
        return withId(post("/organizer/payouts"), user, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"password\":\"" + PASSWORD + "\"}");
    }

    private MockHttpServletRequestBuilder withId(MockHttpServletRequestBuilder request, User user, String requestId) {
        return request.header("Authorization", bearer(user)).header("X-Request-Id", requestId);
    }

    private static String requestId() {
        return "audit-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<Map<String, Object>> audit(UUID targetId) {
        return jdbc.queryForList("""
                SELECT id, actor_type, actor_id, action, target_type, target_id, details::text AS details,
                       correlation_id, created_at
                  FROM audit_log
                 WHERE target_id = ?
                 ORDER BY created_at, id
                """, targetId).stream()
                .sorted((a, b) -> {
                    var byTime = ((Timestamp) a.get("created_at")).compareTo((Timestamp) b.get("created_at"));
                    return byTime != 0 ? byTime : Integer.compare(order(a), order(b));
                })
                .toList();
    }

    private static int order(Map<String, Object> row) {
        return AuditAction.valueOf((String) row.get("action")).ordinal();
    }

    private Map<String, Object> only(List<Map<String, Object>> rows, String requestId) {
        var matching = rows.stream().filter(row -> requestId.equals(row.get("correlation_id"))).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private void assertRow(Map<String, Object> row, String action, User actor, String requestId) {
        assertThat(row.get("action")).isEqualTo(action);
        assertThat(row.get("actor_type")).isEqualTo("USER");
        assertThat(row.get("actor_id")).isEqualTo(actor.getId());
        assertThat(row.get("correlation_id")).isEqualTo(requestId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(Map<String, Object> row) {
        return JSON.readValue((String) row.get("details"), Map.class);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
    }

    private void assertNoSensitiveData() {
        var all = jdbc.queryForList("SELECT details::text FROM audit_log", String.class);
        assertThat(all).allSatisfy(details -> assertThat(details)
                .doesNotContain(CPF, "529.982", "maria.silva", "m***", "***.", PASSWORD, "password", "pixKey\"", "document\""));
    }
}
