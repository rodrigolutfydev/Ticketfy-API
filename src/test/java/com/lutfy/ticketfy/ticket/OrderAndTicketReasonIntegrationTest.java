package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderAndTicketReasonIntegrationTest extends TicketTransferTestBase {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Um pedido que já foi pago não pode gerar um novo Pix.
     *
     * O endpoint antigo /payment foi desativado.
     * O novo endpoint /payment/pix retorna HTTP 409
     * com o tipo invalid-order-state.
     */
    @Test
    void payingAPaidOrderIsAlreadyPaid() throws Exception {

        var buyer = user("USER");

        var orderId = paidOrder(buyer, 1);

        mockMvc.perform(
                        post("/orders/{id}/payment/pix", orderId)
                                .header("Authorization", bearer(buyer))
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath(
                                "$.type",
                                endsWith("/invalid-order-state")
                        )
                )
                .andExpect(
                        jsonPath("$.reason").doesNotExist()
                );
    }

    /**
     * Um pedido pago não pode ser cancelado
     * como se ainda estivesse pendente.
     */
    @Test
    void cancellingAPaidOrderIsNotPending() throws Exception {

        var buyer = user("USER");

        var orderId = paidOrder(buyer, 1);

        expectReason(
                mockMvc.perform(
                        delete("/orders/{id}", orderId)
                                .header("Authorization", bearer(buyer))
                ),
                409,
                "invalid-order-state",
                "not-pending"
        );
    }

    /**
     * Não é permitido reembolsar um pedido
     * que ainda não foi pago.
     */
    @Test
    void refundingAPendingOrderIsNotPaid() throws Exception {

        var buyer = user("USER");

        var orderId = orderService.create(
                new OrderCreationDTO(
                        List.of(
                                new OrderItemRequestDTO(ticketTypeId, 1)
                        )
                ),
                null,
                buyer
        ).id();

        expectReason(
                refund(buyer, orderId),
                409,
                "invalid-order-state",
                "not-paid"
        );
    }

    /**
     * Não é permitido reembolsar um pedido
     * que possui ingressos utilizados.
     */
    @Test
    void refundingAnOrderWithUsedTicketsIsTicketsUsed() throws Exception {

        var buyer = user("USER");

        var orderId = paidOrder(buyer, 1);

        checkIn(ticketOf(orderId))
                .andExpect(status().isOk());

        expectReason(
                refund(buyer, orderId),
                409,
                "invalid-order-state",
                "tickets-used"
        );
    }

    /**
     * O reembolso deve ser recusado quando
     * o prazo permitido estiver encerrado.
     */
    @Test
    void refundingCloseToTheEventIsRefundPeriodEnded() throws Exception {

        var buyer = user("USER");

        var orderId = paidOrder(buyer, 1);

        var startsAt = Instant.now()
                .plus(Duration.ofHours(1));

        jdbc.update(
                "UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(startsAt),
                Timestamp.from(
                        startsAt.plus(Duration.ofHours(2))
                ),
                eventId
        );

        expectReason(
                refund(buyer, orderId),
                409,
                "invalid-order-state",
                "refund-period-ended"
        );
    }

    /**
     * Um ingresso utilizado não pode passar
     * por um segundo check-in.
     *
     * A resposta deve informar quando
     * o ingresso foi utilizado.
     */
    @Test
    void checkInOfAUsedTicketIsAlreadyUsedWithTheCheckInTime()
            throws Exception {

        var ticketId = ticketOf(
                paidOrder(user("USER"), 1)
        );

        checkIn(ticketId)
                .andExpect(status().isOk());

        var usedAt = jdbc.queryForObject(
                "SELECT used_at FROM tickets WHERE id = ?",
                Timestamp.class,
                ticketId
        ).toInstant();

        expectReason(
                checkIn(ticketId),
                409,
                "invalid-ticket-state",
                "already-used"
        )
                .andExpect(
                        jsonPath("$.usedAt")
                                .value(usedAt.toString())
                );
    }

    /**
     * Um ingresso cancelado após reembolso
     * não pode ser utilizado.
     */
    @Test
    void checkInOfACancelledTicketIsNotValidWithoutUsedAt()
            throws Exception {

        var buyer = user("USER");

        var orderId = paidOrder(buyer, 1);

        var ticketId = ticketOf(orderId);

        refund(buyer, orderId)
                .andExpect(status().isOk());

        expectReason(
                checkIn(ticketId),
                409,
                "invalid-ticket-state",
                "not-valid"
        )
                .andExpect(
                        jsonPath("$.usedAt").doesNotExist()
                );
    }

    /**
     * Ingressos de eventos cancelados
     * não podem realizar check-in.
     */
    @Test
    void checkInAfterCancellationIsEventCancelled()
            throws Exception {

        var ticketId = ticketOf(
                paidOrder(user("USER"), 1)
        );

        cancelEvent()
                .andExpect(status().isOk());

        expectReason(
                checkIn(ticketId),
                409,
                "invalid-ticket-state",
                "event-cancelled"
        );
    }

    /**
     * Não permite cancelar duas vezes
     * o mesmo evento.
     *
     * Também impede cancelar eventos encerrados.
     */
    @Test
    void cancellingTwiceIsCancelledAndCancellingAnEndedEventIsEnded()
            throws Exception {

        cancelEvent()
                .andExpect(status().isOk());

        expectReason(
                cancelEvent(),
                409,
                "invalid-event-state",
                "cancelled"
        );

        var endedEvent = insertEvent(
                organizer.getId()
        );

        var past = Instant.now()
                .minus(Duration.ofDays(2));

        jdbc.update(
                "UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(past),
                Timestamp.from(
                        past.plus(Duration.ofHours(3))
                ),
                endedEvent
        );

        expectReason(
                mockMvc.perform(
                        post("/events/{id}/cancel", endedEvent)
                                .header(
                                        "Authorization",
                                        bearer(organizer)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"reason\":\"Teste\"}"
                                )
                ),
                409,
                "invalid-event-state",
                "ended"
        );
    }

    /**
     * Solicita reembolso de um pedido.
     */
    private ResultActions refund(
            User buyer,
            UUID orderId
    ) throws Exception {

        return mockMvc.perform(
                post("/orders/{id}/refund", orderId)
                        .header(
                                "Authorization",
                                bearer(buyer)
                        )
        );
    }

    /**
     * Realiza check-in de um ingresso.
     */
    private ResultActions checkIn(
            UUID ticketId
    ) throws Exception {

        return mockMvc.perform(
                post(
                        "/tickets/{code}/check-in",
                        code(ticketId)
                )
                        .header(
                                "Authorization",
                                bearer(organizer)
                        )
        );
    }

    /**
     * Cancela o evento utilizado nos testes.
     */
    private ResultActions cancelEvent()
            throws Exception {

        return mockMvc.perform(
                post("/events/{id}/cancel", eventId)
                        .header(
                                "Authorization",
                                bearer(organizer)
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                "{\"reason\":\"Teste\"}"
                        )
        );
    }

    /**
     * Valida o status HTTP e o motivo
     * estruturado de uma falha.
     */
    private static ResultActions expectReason(
            ResultActions result,
            int expectedStatus,
            String slug,
            String reason
    ) throws Exception {

        return result
                .andExpect(
                        status().is(expectedStatus)
                )
                .andExpect(
                        jsonPath(
                                "$.type",
                                endsWith("/" + slug)
                        )
                )
                .andExpect(
                        jsonPath("$.reason")
                                .value(reason)
                );
    }

    /**
     * Gera o cabeçalho de autenticação
     * para o usuário informado.
     */
    private String bearer(User user) {

        return "Bearer " + accessToken(user);
    }
}