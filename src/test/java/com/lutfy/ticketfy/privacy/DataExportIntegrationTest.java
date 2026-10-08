package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.ticket.TicketTransferRequestDTO;
import com.lutfy.ticketfy.ticket.TicketTransferService;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataExportIntegrationTest extends PrivacyTestBase {

    @Autowired
    private TicketTransferService transferService;

    @Test
    void exportsEveryBlockWithoutThirdPartyData() throws Exception {
        var exporter = person("ORGANIZER", "Olga");
        var friend = person("USER", "Rafael");
        var otherOrganizer = person("ORGANIZER", "Otavio");
        var otherEvent = upcomingEvent(otherOrganizer);
        var otherLot = insertTicketType(otherEvent, "50.00");
        jdbc.update("""
                INSERT INTO coupons (id, event_id, code, discount_type, discount_value)
                VALUES (?, ?, 'AMIGO10', 'PERCENT', 10)
                """, UUID.randomUUID(), otherEvent);

        var orderId = orderService.create(new OrderCreationDTO(
                List.of(new OrderItemRequestDTO(otherLot, 2)), "amigo10"), null, exporter).id();
        paymentService.paySimulated(orderId, exporter);
        var sentTicket = jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ? ORDER BY id LIMIT 1",
                UUID.class, orderId);
        transferService.transfer(sentTicket, new TicketTransferRequestDTO(friend.getEmail(), PASSWORD), exporter);
        var friendOrder = paidOrder(friend, otherLot, 1);
        var receivedTicket = jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ?", UUID.class, friendOrder);
        transferService.transfer(receivedTicket, new TicketTransferRequestDTO(exporter.getEmail(), PASSWORD), friend);

        releasedSales(exporter, "100.00");
        registerAccount(exporter);
        requestPayout(exporter, "20.00", PASSWORD, null).andExpect(status().isCreated());

        var result = exportData(exporter, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"ticketfy-meus-dados-")));
        var raw = result.andReturn().getResponse().getContentAsString();
        var export = JSON.readTree(raw);

        assertThat(export.get("schemaVersion").asInt()).isEqualTo(1);
        var profile = export.get("profile");
        assertThat(profile.get("id").asString()).isEqualTo(exporter.getId().toString());
        assertThat(profile.get("name").asString()).isEqualTo(exporter.getName());
        assertThat(profile.get("email").asString()).isEqualTo(exporter.getEmail());
        assertThat(profile.get("avatarUrl").asString()).isEqualTo(exporter.getAvatarUrl());

        var order = find(export.get("orders"), "id", orderId.toString());
        assertThat(order.get("status").asString()).isEqualTo("PAID");
        assertThat(order.get("couponCode").asString()).isEqualTo("AMIGO10");
        assertThat(order.get("subtotalAmount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(order.get("discountAmount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(order.get("event").get("id").asString()).isEqualTo(otherEvent.toString());
        assertThat(order.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(order.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(order.get("payments").get(0).get("status").asString()).isEqualTo("APPROVED");

        var ticketIds = values(export.get("tickets"), "id");
        assertThat(ticketIds).contains(receivedTicket.toString()).doesNotContain(sentTicket.toString());

        var transfers = export.get("transfers");
        assertThat(transfers).hasSize(2);
        assertThat(find(transfers, "ticketId", sentTicket.toString()).get("direction").asString()).isEqualTo("SENT");
        assertThat(find(transfers, "ticketId", receivedTicket.toString()).get("direction").asString())
                .isEqualTo("RECEIVED");
        assertThat(find(transfers, "ticketId", sentTicket.toString()).propertyNames())
                .containsExactlyInAnyOrder("ticketId", "direction", "event", "ticketTypeName", "transferredAt");

        var organizerBlock = export.get("organizer");
        assertThat(organizerBlock.get("events")).hasSize(1);
        var event = organizerBlock.get("events").get(0);
        assertThat(event.get("ticketTypes")).hasSize(1);
        assertThat(event.get("sales").get("paidOrders").asLong()).isEqualTo(1);
        assertThat(organizerBlock.get("balance").get("total").decimalValue()).isEqualByComparingTo("75.00");
        assertThat(values(organizerBlock.get("ledger"), "type")).contains("SALE_CREDIT", "PAYOUT_DEBIT");
        var payout = organizerBlock.get("payouts").get(0);
        assertThat(payout.get("amount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(payout.get("document").asString()).isEqualTo(CPF);
        assertThat(payout.get("pixKey").asString()).isEqualTo("maria.silva@example.com");
        var account = organizerBlock.get("payoutAccount");
        assertThat(account.get("document").asString()).isEqualTo(CPF);
        assertThat(account.get("pixKey").asString()).isEqualTo("maria.silva@example.com");
        assertThat(account.get("holderName").asString()).isEqualTo("Maria da Silva");

        for (var thirdParty : List.of(friend, otherOrganizer, buyer)) {
            assertThat(raw).doesNotContain(thirdParty.getEmail(), thirdParty.getName(), thirdParty.getId().toString());
        }

        var details = jdbc.queryForObject("""
                SELECT details::text FROM audit_log
                 WHERE action = 'DATA_EXPORTED' AND target_id = ? AND actor_id = ?
                """, String.class, exporter.getId(), exporter.getId());
        var counts = JSON.readTree(details);
        assertThat(counts.get("orders").asInt()).isEqualTo(1);
        assertThat(counts.get("transfers").asInt()).isEqualTo(2);
        assertThat(counts.get("payouts").asInt()).isEqualTo(1);
        assertThat(details).doesNotContain(exporter.getEmail(), CPF);
    }

    @Test
    void buyerExportHasNoOrganizerBlock() throws Exception {
        var user = person("USER", "Bianca");

        exportData(user, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isEmpty())
                .andExpect(jsonPath("$.organizer").doesNotExist());
    }

    @Test
    void wrongPasswordIsRejectedAndNotAudited() throws Exception {
        var user = person("USER", "Caio");

        exportData(user, "senha-errada")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-password")));

        assertThat(exports(user)).isZero();
    }

    @Test
    void allowsThreeExportsPerDay() throws Exception {
        var user = person("USER", "Dora");
        var token = bearer(user);

        for (int i = 0; i < 3; i++) {
            exportData(token, PASSWORD).andExpect(status().isOk());
        }
        exportData(token, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/too-many-data-exports")));
        assertThat(exports(user)).isEqualTo(3);

        travel(java.time.Duration.ofHours(24).plusMinutes(1));
        exportData(user, PASSWORD).andExpect(status().isOk());
    }

    private int exports(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'DATA_EXPORTED' AND target_id = ?",
                Integer.class, user.getId());
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (var node : array) {
            if (value.equals(node.get(field).asString())) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + "=" + value);
    }

    private static List<String> values(JsonNode array, String field) {
        var values = new ArrayList<String>();
        array.forEach(node -> values.add(node.get(field).asString()));
        return values;
    }
}
