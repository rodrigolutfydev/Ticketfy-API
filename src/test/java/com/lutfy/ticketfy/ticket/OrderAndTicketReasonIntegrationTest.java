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

    @Test
    void payingAPaidOrderIsAlreadyPaid() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 1);

        expectReason(mockMvc.perform(post("/orders/{id}/payment", orderId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PIX\"}")),
                409, "invalid-payment-state", "already-paid");
    }

    @Test
    void cancellingAPaidOrderIsNotPending() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 1);

        expectReason(mockMvc.perform(delete("/orders/{id}", orderId).header("Authorization", bearer(buyer))),
                409, "invalid-order-state", "not-pending");
    }

    @Test
    void refundingAPendingOrderIsNotPaid() throws Exception {
        var buyer = user("USER");
        var orderId = orderService.create(
                new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, 1))), null, buyer).id();

        expectReason(refund(buyer, orderId), 409, "invalid-order-state", "not-paid");
    }

    @Test
    void refundingAnOrderWithUsedTicketsIsTicketsUsed() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 1);
        checkIn(ticketOf(orderId)).andExpect(status().isOk());

        expectReason(refund(buyer, orderId), 409, "invalid-order-state", "tickets-used");
    }

    @Test
    void refundingCloseToTheEventIsRefundPeriodEnded() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 1);
        var startsAt = Instant.now().plus(Duration.ofHours(1));
        jdbc.update("UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(startsAt), Timestamp.from(startsAt.plus(Duration.ofHours(2))), eventId);

        expectReason(refund(buyer, orderId), 409, "invalid-order-state", "refund-period-ended");
    }

    @Test
    void checkInOfAUsedTicketIsNotValid() throws Exception {
        var ticketId = ticketOf(paidOrder(user("USER"), 1));
        checkIn(ticketId).andExpect(status().isOk());

        expectReason(checkIn(ticketId), 409, "invalid-ticket-state", "not-valid");
    }

    @Test
    void checkInAfterCancellationIsEventCancelled() throws Exception {
        var ticketId = ticketOf(paidOrder(user("USER"), 1));
        cancelEvent().andExpect(status().isOk());

        expectReason(checkIn(ticketId), 409, "invalid-ticket-state", "event-cancelled");
    }

    @Test
    void cancellingTwiceIsCancelledAndCancellingAnEndedEventIsEnded() throws Exception {
        cancelEvent().andExpect(status().isOk());
        expectReason(cancelEvent(), 409, "invalid-event-state", "cancelled");

        var endedEvent = insertEvent(organizer.getId());
        var past = Instant.now().minus(Duration.ofDays(2));
        jdbc.update("UPDATE events SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.from(past), Timestamp.from(past.plus(Duration.ofHours(3))), endedEvent);
        expectReason(mockMvc.perform(post("/events/{id}/cancel", endedEvent)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Teste\"}")),
                409, "invalid-event-state", "ended");
    }

    private ResultActions refund(User buyer, UUID orderId) throws Exception {
        return mockMvc.perform(post("/orders/{id}/refund", orderId).header("Authorization", bearer(buyer)));
    }

    private ResultActions checkIn(UUID ticketId) throws Exception {
        return mockMvc.perform(post("/tickets/{code}/check-in", code(ticketId)).header("Authorization", bearer(organizer)));
    }

    private ResultActions cancelEvent() throws Exception {
        return mockMvc.perform(post("/events/{id}/cancel", eventId)
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Teste\"}"));
    }

    private static void expectReason(ResultActions result, int status, String slug, String reason) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.type", endsWith("/" + slug)))
                .andExpect(jsonPath("$.reason").value(reason));
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
