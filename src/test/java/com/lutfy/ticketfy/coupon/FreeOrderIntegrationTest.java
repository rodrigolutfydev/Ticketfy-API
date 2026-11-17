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

import tools.jackson.databind.json.JsonMapper;

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

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void organizerCreatesAFreeTicketType() throws Exception {

        mockMvc.perform(post("/events/{eventId}/ticket-types", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gratuito",
                                  "price": 0,
                                  "quantityTotal": 50,
                                  "maxPerOrder": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(0));

        mockMvc.perform(post("/events/{eventId}/ticket-types", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Negativo",
                                  "price": -1,
                                  "quantityTotal": 50
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freeOrderIsConfirmedWithoutPaymentOrLedgerEntry() throws Exception {

        var lot = lot(eventId, "0.00", 10);
        var buyer = user("USER");

        // 1. Criar pedido gratuito
        var response = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "ticketTypeId": "%s",
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """.formatted(lot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 2. Extrair ID do pedido
        UUID orderId = UUID.fromString(
                jsonMapper.readTree(response)
                        .get("id")
                        .asString()
        );

        // 3. Pedido deve estar confirmado
        assertThat(orderStatus(orderId))
                .isEqualTo("PAID");

        // 4. Pedido gratuito não deve gerar pagamento
        assertThat(count(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                orderId
        )).isZero();

        // 5. Pedido gratuito não deve gerar lançamento financeiro
        assertThat(count(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?",
                orderId
        )).isZero();

        // 6. Deve emitir exatamente dois ingressos
        assertThat(count(
                "SELECT COUNT(*) FROM tickets WHERE order_id = ?",
                orderId
        )).isEqualTo(2);

        // 7. Deve atualizar o estoque vendido
        assertThat(count(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?",
                lot
        )).isEqualTo(2);

        // 8. Pedido já pago não pode gerar Pix
        mockMvc.perform(post("/orders/{id}/payment/pix", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isConflict());

        // 9. Confirmar que a tentativa não alterou o pedido
        assertThat(orderStatus(orderId))
                .isEqualTo("PAID");

        // 10. Nenhum pagamento deve ter sido criado
        assertThat(count(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                orderId
        )).isZero();

        // 11. Nenhum lançamento financeiro deve ter sido criado
        assertThat(count(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?",
                orderId
        )).isZero();

        // 12. Não pode emitir ingressos duplicados
        assertThat(count(
                "SELECT COUNT(*) FROM tickets WHERE order_id = ?",
                orderId
        )).isEqualTo(2);

        // 13. Estoque vendido deve permanecer inalterado
        assertThat(count(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?",
                lot
        )).isEqualTo(2);

        // 14. Dashboard deve contabilizar os ingressos gratuitos
        mockMvc.perform(get("/events/{id}/dashboard", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(2))
                .andExpect(jsonPath("$.dailySales[0].tickets").value(2));
    }

    @Test
    void freeOrdersStillRespectMaxPerOrderAndStock() {

        var lot = lot(eventId, "0.00", 12);

        assertThatThrownBy(() ->
                order(user("USER"), lot, 11, null)
        ).isInstanceOf(MaxPerOrderExceededException.class);

        order(user("USER"), lot, 10, null);

        assertThatThrownBy(() ->
                order(user("USER"), lot, 3, null)
        ).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void refundOfAFreeOrderCancelsTicketsAndReleasesStock() {

        var lot = lot(eventId, "0.00", 10);
        var buyer = user("USER");
        var order = order(buyer, lot, 2, null);

        // 1. Reembolsar/cancelar pedido gratuito
        orderService.refund(order.id(), buyer);

        // 2. Pedido deve estar reembolsado
        assertThat(orderStatus(order.id()))
                .isEqualTo("REFUNDED");

        // 3. Ingressos devem estar cancelados
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM tickets
                WHERE order_id = ?
                  AND status = 'CANCELLED'
                """,
                order.id()
        )).isEqualTo(2);

        // 4. Estoque deve ser liberado
        assertThat(count(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?",
                lot
        )).isZero();

        // 5. Não deve existir lançamento financeiro
        assertThat(count(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?",
                order.id()
        )).isZero();

        // 6. Não pode reembolsar duas vezes
        assertThatThrownBy(() ->
                orderService.refund(order.id(), buyer)
        ).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void eventCancellationCancelsFreeOrdersWithoutRefund() throws Exception {

        var lot = lot(eventId, "0.00", 10);
        var order = order(user("USER"), lot, 1, null);

        // 1. Cancelar evento
        mockMvc.perform(post("/events/{id}/cancel", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Chuva"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(0))
                .andExpect(jsonPath("$.cancelledFreeOrders").value(1));

        // 2. Pedido deve estar cancelado
        assertThat(orderStatus(order.id()))
                .isEqualTo("CANCELLED");

        // 3. Ingresso deve estar cancelado
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM tickets
                WHERE order_id = ?
                  AND status = 'CANCELLED'
                """,
                order.id()
        )).isEqualTo(1);

        // 4. Estoque deve ser liberado
        assertThat(count(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?",
                lot
        )).isZero();

        // 5. Não deve gerar lançamento financeiro
        assertThat(count(
                "SELECT COUNT(*) FROM organizer_ledger_entries WHERE order_id = ?",
                order.id()
        )).isZero();
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}