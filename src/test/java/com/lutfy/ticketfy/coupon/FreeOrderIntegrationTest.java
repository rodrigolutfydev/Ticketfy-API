package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.exception.InsufficientStockException;
import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.infra.exception.MaxPerOrderExceededException;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FreeOrderIntegrationTest extends CouponTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void organizerCreatesAFreeTicketType() throws Exception {
        mockMvc.perform(post("/events/{eventId}/ticket-types", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gratuito\",\"price\":0,\"quantityTotal\":50,\"maxPerOrder\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(0));

        mockMvc.perform(post("/events/{eventId}/ticket-types", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Negativo\",\"price\":-1,\"quantityTotal\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freeOrderIsConfirmedWithoutPaymentOrLedgerEntry() throws Exception {
        var lot = lot(eventId, "0.00", 10);
        var buyer = user("USER");

        var created = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        var orderId = UUID.fromString(created.replaceAll("^\\{\"id\":\"([^\"]+)\".*", "$1"));

        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ?", orderId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?", orderId)).isZero();
        assertThat(count("SELECT quantity_sold FROM ticket_types WHERE id = ?", lot)).isEqualTo(2);

        mockMvc.perform(post("/orders/{id}/payment", orderId).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/events/{id}/dashboard", eventId).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(2))
                .andExpect(jsonPath("$.dailySales[0].tickets").value(2));
    }

    @Test
    void freeOrdersStillRespectMaxPerOrderAndStock() {
        var lot = lot(eventId, "0.00", 12);

        assertThatThrownBy(() -> order(user("USER"), lot, 11, null))
                .isInstanceOf(MaxPerOrderExceededException.class);
        order(user("USER"), lot, 10, null);
        assertThatThrownBy(() -> order(user("USER"), lot, 3, null))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void refundOfAFreeOrderCancelsTicketsAndReleasesStock() {
        var lot = lot(eventId, "0.00", 10);
        var buyer = user("USER");
        var order = order(buyer, lot, 2, null);

        orderService.refund(order.id(), buyer);

        assertThat(orderStatus(order.id())).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM tickets WHERE order_id = ? AND status = 'CANCELLED'", order.id()))
                .isEqualTo(2);
        assertThat(count("SELECT quantity_sold FROM ticket_types WHERE id = ?", lot)).isZero();
        assertThat(count("SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?", order.id())).isZero();
        assertThatThrownBy(() -> orderService.refund(order.id(), buyer)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void eventCancellationRefundsFreeOrders() throws Exception {
        var lot = lot(eventId, "0.00", 10);
        var order = order(user("USER"), lot, 1, null);

        mockMvc.perform(post("/events/{id}/cancel", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Chuva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(1));

        assertThat(orderStatus(order.id())).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?", order.id())).isZero();
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
