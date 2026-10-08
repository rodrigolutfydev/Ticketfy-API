package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerCsvIntegrationTest extends PayoutTestBase {

    private static final String HEADER = "Data;Tipo;Valor;Evento;Pedido;Saque;Lançamento";

    @Test
    void exportsLedgerWithFormulaProtectionAndCommaDecimals() throws Exception {
        var formulaEvent = event("=SOMA(1;2)");
        var normalEvent = event("Show de Rock");
        var refunded = paidOrder(formulaEvent, "10.10");
        paidOrder(normalEvent, "100.00");
        mockMvc.perform(post("/orders/" + refunded + "/refund").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk());

        var response = mockMvc.perform(get("/organizer/ledger.csv").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"extrato-ticketfy-" + LocalDate.now(ZoneId.of("America/Sao_Paulo")) + ".csv\""))
                .andReturn().getResponse();
        var bytes = response.getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        var csv = new String(bytes, StandardCharsets.UTF_8).substring(1);
        assertThat(csv).endsWith("\r\n");
        var lines = csv.split("\r\n");

        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines).hasSize(4);
        var refund = line(lines, refunded.toString(), "Reembolso");
        var refundFields = refund.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        assertThat(refundFields[0]).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        assertThat(refundFields[1]).isEqualTo("Reembolso");
        assertThat(refundFields[2]).isEqualTo("-9,59");
        assertThat(refundFields[3]).isEqualTo("\"'=SOMA(1;2)\"");
        assertThat(refundFields[4]).isEqualTo(refunded.toString());
        assertThat(refundFields[5]).isEmpty();
        var credit = line(lines, refunded.toString(), "Venda");
        assertThat(credit).contains(";Venda;9,59;\"'=SOMA(1;2)\";");
        var normal = Arrays.stream(lines).filter(line -> line.contains("Show de Rock")).toList();
        assertThat(normal).hasSize(1);
        assertThat(normal.get(0)).contains(";Venda;95,00;Show de Rock;").doesNotContain("'Show");
    }

    @Test
    void exportAppliesTheLedgerFilters() throws Exception {
        var first = event("Primeiro");
        var second = event("Segundo");
        paidOrder(first, "20.00");
        paidOrder(second, "30.00");

        var byEvent = csv("?eventId=" + first);
        assertThat(byEvent).hasSize(2);
        assertThat(byEvent.get(1)).contains(";Primeiro;").contains("19,00");

        var today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        assertThat(csv("?from=" + today + "&to=" + today)).hasSize(3);
        assertThat(csv("?to=" + today.minusDays(1))).containsExactly(HEADER);
        mockMvc.perform(get("/organizer/ledger.csv?from=" + today + "&to=" + today.minusDays(1))
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isBadRequest());
        assertThat(csvFor(user("ORGANIZER"), "")).containsExactly(HEADER);
        mockMvc.perform(get("/organizer/ledger.csv").header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payoutEntriesExportNegativeNumbersWithoutApostrophe() throws Exception {
        releasedSales(organizer, "100.00");
        registerAccount(organizer);
        var payoutId = body(requestPayout(organizer, "20.00", PASSWORD, null)).get("id").asString();

        var payoutLine = csv("").stream().filter(line -> line.contains(payoutId)).toList();

        assertThat(payoutLine).hasSize(1);
        assertThat(payoutLine.get(0)).contains(";Saque;-20,00;;;" + payoutId + ";").doesNotContain("'-");
    }

    private UUID event(String name) {
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        var id = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        jdbc.update("UPDATE events SET name = ? WHERE id = ?", name, id);
        return id;
    }

    private UUID paidOrder(UUID eventId, String price) {
        var ticketTypeId = insertTicketType(eventId, price);
        var orderId = orderService.create(
                new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, 1))), null, buyer).id();
        paymentService.paySimulated(orderId, buyer);
        return orderId;
    }

    private List<String> csv(String query) throws Exception {
        return csvFor(organizer, query);
    }

    private List<String> csvFor(User user, String query) throws Exception {
        var body = mockMvc.perform(get("/organizer/ledger.csv" + query).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return List.of(body.substring(1).split("\r\n"));
    }

    private static String line(String[] lines, String orderId, String type) {
        var matching = Arrays.stream(lines).filter(line -> line.contains(orderId) && line.contains(";" + type + ";")).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }
}
