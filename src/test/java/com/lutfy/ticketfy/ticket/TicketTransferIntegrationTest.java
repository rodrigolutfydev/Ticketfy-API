package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TicketTransferIntegrationTest extends TicketTransferTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void transferChangesOwnerAndCodeAndInvalidatesTheOldCode() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        var oldCode = code(ticketId);

        transfer(buyer, ticketId, recipient.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.transferredAt").isNotEmpty())
                .andExpect(jsonPath("$.code").doesNotExist());

        var newCode = code(ticketId);
        assertThat(owner(ticketId)).isEqualTo(recipient.getId());
        assertThat(newCode).isNotEqualTo(oldCode).hasSize(16).matches("[ABCDEFGHJKMNPQRSTVWXYZ0-9]+");

        mockMvc.perform(post("/tickets/{code}/check-in", oldCode).header("Authorization", bearer(organizer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", endsWith("/ticket-not-found")));
        mockMvc.perform(post("/tickets/{code}/check-in", newCode).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));
    }

    @Test
    void recipientCanTransferAgainUntilTheLimit() throws Exception {
        var first = user("USER");
        var second = user("USER");
        var third = user("USER");
        var ticketId = ticketOf(paidOrder(first, 1));

        transfer(first, ticketId, second.getEmail(), PASSWORD).andExpect(status().isOk());
        transfer(second, ticketId, third.getEmail(), PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(third)))
                .andExpect(jsonPath("$.content[0].id").value(ticketId.toString()))
                .andExpect(jsonPath("$.content[0].transferable").value(true));
        transfer(third, ticketId, first.getEmail(), PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.content[0].transferable").value(false));
        expectProblem(transfer(first, ticketId, second.getEmail(), PASSWORD), 409, "ticket-transfer-limit-reached");
        assertThat(owner(ticketId)).isEqualTo(first.getId());
        assertThat(transfers(ticketId)).isEqualTo(3);
    }

    @Test
    void refusesUsedTickets() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        mockMvc.perform(post("/tickets/{code}/check-in", code(ticketId)).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());

        expectProblem(transfer(buyer, ticketId, user("USER").getEmail(), PASSWORD), 409, "invalid-ticket-state");
        assertThat(owner(ticketId)).isEqualTo(buyer.getId());
    }

    @Test
    void refusesTicketsOfCancelledEvents() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        mockMvc.perform(post("/events/{id}/cancel", eventId).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());

        expectProblem(transfer(buyer, ticketId, user("USER").getEmail(), PASSWORD), 409, "invalid-event-state");
    }

    @Test
    void refusesTicketsOfEventsThatAlreadyStarted() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        jdbc.update("UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))),
                Timestamp.from(Instant.now().plus(Duration.ofHours(2))), eventId);

        expectProblem(transfer(buyer, ticketId, user("USER").getEmail(), PASSWORD), 409, "ticket-transfer-closed");
        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.content[0].transferable").value(false));
    }

    @Test
    void refusesTransferToYourself() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));

        expectProblem(transfer(buyer, ticketId, buyer.getEmail().toUpperCase(), PASSWORD), 400, "self-transfer");
    }

    @Test
    void refusesWrongPassword() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));

        expectProblem(transfer(buyer, ticketId, user("USER").getEmail(), "senha-errada"), 403, "invalid-password");
        assertThat(owner(ticketId)).isEqualTo(buyer.getId());
    }

    @Test
    void unknownRecipientGetsAGenericErrorAndIsRateLimited() throws Exception {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));

        for (int i = 0; i < 5; i++) {
            transfer(buyer, ticketId, "ninguem-" + i + "@test.com", PASSWORD)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.type", endsWith("/transfer-recipient-unavailable")))
                    .andExpect(jsonPath("$.detail").value("The ticket cannot be transferred to this recipient"));
        }
        transfer(buyer, ticketId, user("USER").getEmail(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type", endsWith("/too-many-transfer-attempts")));
        assertThat(owner(ticketId)).isEqualTo(buyer.getId());
    }

    @Test
    void onlyTheCurrentOwnerSeesTheCodeAndOrdersStayPrivate() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var stranger = user("USER");
        var orderId = paidOrder(buyer, 2);
        var transferred = ticketOf(orderId);
        transfer(buyer, transferred, recipient.getEmail(), PASSWORD).andExpect(status().isOk());
        var kept = jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ? AND id <> ?", UUID.class,
                orderId, transferred);

        mockMvc.perform(get("/orders/{id}", orderId).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(160.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + transferred + "')].transferred").value(true))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + transferred + "')].code").value((Object) null))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + kept + "')].transferred").value(false))
                .andExpect(jsonPath("$.tickets[?(@.id == '" + kept + "')].code").value(code(kept)));
        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kept.toString()));

        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(recipient)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value(code(transferred)));
        expectProblem(mockMvc.perform(get("/orders/{id}", orderId).header("Authorization", bearer(recipient))),
                404, "order-not-found");
        expectProblem(mockMvc.perform(post("/orders/{id}/refund", orderId).header("Authorization", bearer(recipient))),
                404, "order-not-found");

        expectProblem(mockMvc.perform(get("/orders/{id}", orderId).header("Authorization", bearer(stranger))),
                404, "order-not-found");
        expectProblem(mockMvc.perform(post("/orders/{id}/payment", orderId).header("Authorization", bearer(stranger))),
                404, "order-not-found");
        expectProblem(transfer(stranger, transferred, buyer.getEmail(), PASSWORD), 404, "ticket-not-found");
        expectProblem(transfer(buyer, transferred, stranger.getEmail(), PASSWORD), 404, "ticket-not-found");
    }

    @Test
    void buyerCannotRefundAnOrderWithTransferredTickets() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 2);
        transfer(buyer, ticketOf(orderId), user("USER").getEmail(), PASSWORD).andExpect(status().isOk());

        expectProblem(mockMvc.perform(post("/orders/{id}/refund", orderId).header("Authorization", bearer(buyer))),
                409, "order-has-transferred-tickets");
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void eventCancellationCancelsTransferredTicketsAndRefundsTheBuyer() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var orderId = paidOrder(buyer, 1);
        var ticketId = ticketOf(orderId);
        transfer(buyer, ticketId, recipient.getEmail(), PASSWORD).andExpect(status().isOk());

        mockMvc.perform(post("/events/{id}/cancel", eventId).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(1));

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("REFUNDED");
        assertThat(ticketStatus(ticketId)).isEqualTo("CANCELLED");
        assertThat(owner(ticketId)).isEqualTo(recipient.getId());
        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(recipient)))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].transferable").value(false));
    }

    @Test
    void recordsHistoryAndAuditWithoutCodes() throws Exception {
        var buyer = user("USER");
        var recipient = user("USER");
        var orderId = paidOrder(buyer, 1);
        var ticketId = ticketOf(orderId);
        var oldCode = code(ticketId);
        transfer(buyer, ticketId, recipient.getEmail(), PASSWORD).andExpect(status().isOk());
        var newCode = code(ticketId);

        var history = jdbc.queryForList("SELECT * FROM ticket_transfers WHERE ticket_id = ?", ticketId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsOnlyKeys("id", "ticket_id", "from_user_id", "to_user_id", "transferred_at");
        assertThat(history.get(0)).containsEntry("from_user_id", buyer.getId()).containsEntry("to_user_id", recipient.getId());

        var audit = jdbc.queryForMap("""
                SELECT actor_type, actor_id, target_type, details::text AS details
                  FROM audit_log WHERE action = 'TICKET_TRANSFERRED' AND target_id = ?
                """, ticketId);
        assertThat(audit).containsEntry("actor_type", "USER").containsEntry("actor_id", buyer.getId())
                .containsEntry("target_type", "TICKET");
        var details = (String) audit.get("details");
        assertThat(details).doesNotContain(oldCode).doesNotContain(newCode);
        assertThat(jsonMapper.readValue(details, Map.class))
                .containsEntry("fromUserId", buyer.getId().toString())
                .containsEntry("toUserId", recipient.getId().toString())
                .containsEntry("orderId", orderId.toString())
                .containsEntry("eventId", eventId.toString())
                .containsEntry("transferNumber", 1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE details::text LIKE ?", Integer.class,
                "%" + oldCode + "%")).isZero();
    }

    @Test
    void transferHistoryIsAppendOnly() {
        var buyer = user("USER");
        var ticketId = ticketOf(paidOrder(buyer, 1));
        transferService.transfer(ticketId, to(user("USER")), buyer);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM ticket_transfers WHERE ticket_id = ?", ticketId))
                .hasMessageContaining("append-only");
    }

    private ResultActions transfer(User sender, UUID ticketId, String email, String password) throws Exception {
        return mockMvc.perform(post("/tickets/{id}/transfer", ticketId)
                .header("Authorization", bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("recipientEmail", email, "password", password))));
    }

    private void expectProblem(ResultActions result, int status, String slug) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.type", endsWith("/" + slug)));
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
