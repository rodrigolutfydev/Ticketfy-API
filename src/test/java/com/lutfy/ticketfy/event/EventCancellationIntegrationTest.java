package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.exception.TicketTypeNotFoundException;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.order.CancelledEventOrderProcessor;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentGateway;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.tickettype.TicketTypeRepository;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventCancellationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventCancellationService cancellationService;

    @Autowired
    private CancelledEventOrderProcessor processor;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private String tag;
    private User organizer;
    private UUID eventId;
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        organizer = user("ORGANIZER");
        eventId = insertEvent(organizer.getId(), Instant.now().plus(Duration.ofDays(30)), null, true);
        ticketTypeId = insertTicketType(eventId, 100);
    }

    @Test
    void refundsPaidOrdersAndCancelsPendingOnes() throws Exception {
        var paid = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) {
            paid.add(paidOrder(user("USER"), 2));
        }
        var pending = List.of(pendingOrder(user("USER"), 1), pendingOrder(user("USER"), 3));

        cancel(organizer, "{\"reason\":\"  Chuva forte  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.refundedOrders").value(3))
                .andExpect(jsonPath("$.cancelledOrders").value(2))
                .andExpect(jsonPath("$.pendingOrders").value(0))
                .andExpect(jsonPath("$.requiresManualAction").value(0));

        for (var orderId : paid) {
            assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
            assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
            assertThat(ticketStatuses(orderId)).hasSize(2).containsOnly("CANCELLED");
        }
        for (var orderId : pending) {
            assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        }
        var buyerOrder = paid.get(0);
        var buyer = userRepository.findById(jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", UUID.class, buyerOrder)).orElseThrow();
        mockMvc.perform(get("/orders/{id}", buyerOrder).header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.eventCancelled").value(true));
        mockMvc.perform(get("/tickets/me").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM events WHERE id = ?", String.class, eventId))
                .isEqualTo("Chuva forte");
    }

    @Test
    void cancelledEventLeavesListingsButKeepsDetails() throws Exception {
        cancel(organizer, null).andExpect(status().isOk());

        mockMvc.perform(get("/events").param("q", tag))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/events").param("q", tag).param("featured", "true"))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.cancellationReason").isEmpty());
        mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/events/mine").header("Authorization", bearer(organizer)))
                .andExpect(jsonPath("$.content[0].cancelledAt").isNotEmpty());
    }

    @Test
    void reservationAfterCancellationFails() throws Exception {
        cancel(organizer, null).andExpect(status().isOk());

        var buyer = user("USER");
        assertThatThrownBy(() -> orderService.create(order(1), null, buyer))
                .isInstanceOf(TicketTypeNotFoundException.class);
        Integer reserved = transactionTemplate.execute(tx -> ticketTypeRepository.reserveStock(ticketTypeId, 1));
        assertThat(reserved).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId))
                .isZero();
    }

    @Test
    void paymentOfPendingOrderAfterCancellationIsRejectedWithoutCharging() throws Exception {
        var buyer = user("USER");
        var orderId = pendingOrder(buyer, 1);
        eventService.markCancelled(eventId, null, organizer);

        mockMvc.perform(post("/orders/{id}/payment", orderId).header("Authorization", bearer(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The event for this order was cancelled"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, orderId)).isZero();
        assertThat(ticketStatuses(orderId)).isEmpty();
    }

    @Test
    void paymentThatSlipsThroughCancellationIsRefundedByTheJob() {
        var orderId = paidOrder(user("USER"), 2);
        eventService.markCancelled(eventId, null, organizer);

        cancellationService.processPendingOrders();

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(ticketStatuses(orderId)).containsOnly("CANCELLED");
    }

    @Test
    void refundFailureDoesNotAffectOtherOrdersAndIsRetriedOnce() throws Exception {
        var first = paidOrder(user("USER"), 1);
        var failing = paidOrder(user("USER"), 1);
        var third = paidOrder(user("USER"), 1);
        var failingPayment = paymentId(failing);
        doThrow(new IllegalStateException("gateway unavailable"))
                .when(paymentGateway).refund(argThat(request -> request != null && failingPayment.equals(request.paymentId())));

        cancel(organizer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(2))
                .andExpect(jsonPath("$.pendingOrders").value(1));

        assertThat(jdbc.queryForObject("SELECT cancelled_at FROM events WHERE id = ?", Timestamp.class, eventId)).isNotNull();
        assertThat(orderStatus(first)).isEqualTo("REFUNDED");
        assertThat(orderStatus(third)).isEqualTo("REFUNDED");
        assertThat(orderStatus(failing)).isEqualTo("PAID");
        assertThat(paymentStatus(failing)).isEqualTo("APPROVED");
        assertThat(ticketStatuses(failing)).containsOnly("VALID");

        doCallRealMethod().when(paymentGateway).refund(argThat(request -> request != null && failingPayment.equals(request.paymentId())));
        cancellationService.processPendingOrders();

        assertThat(orderStatus(failing)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(failing)).isEqualTo("REFUNDED");
        assertThat(ticketStatuses(failing)).containsOnly("CANCELLED");
    }

    @Test
    void reprocessingRefundedOrderDoesNotRefundTwice() throws Exception {
        var orderId = paidOrder(user("USER"), 2);
        var paymentId = paymentId(orderId);
        cancel(organizer, null).andExpect(jsonPath("$.refundedOrders").value(1));
        var reference = jdbc.queryForObject("SELECT refund_reference FROM payments WHERE id = ?", String.class, paymentId);
        clearInvocations(paymentGateway);

        assertThat(processor.process(orderId)).isEqualTo(CancelledEventOrderProcessor.Outcome.SKIPPED);
        cancellationService.processPendingOrders();
        cancel(organizer, null).andExpect(status().isConflict());

        verify(paymentGateway, never()).refund(argThat(request -> request != null && paymentId.equals(request.paymentId())));
        assertThat(jdbc.queryForObject("SELECT refund_reference FROM payments WHERE id = ?", String.class, paymentId))
                .isEqualTo(reference);
        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
    }

    @Test
    void orderWithUsedTicketRequiresManualActionAndLeavesTheJob() throws Exception {
        var orderId = paidOrder(user("USER"), 2);
        var code = jdbc.queryForList("SELECT code FROM tickets WHERE order_id = ?", String.class, orderId).get(0);
        mockMvc.perform(post("/tickets/{code}/check-in", code).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
        clearInvocations(paymentGateway);

        cancel(organizer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(0))
                .andExpect(jsonPath("$.pendingOrders").value(0))
                .andExpect(jsonPath("$.requiresManualAction").value(1));
        cancellationService.processPendingOrders();

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(ticketStatuses(orderId)).containsExactlyInAnyOrder("USED", "VALID");
        verify(paymentGateway, never()).refund(argThat(request -> request != null));
    }

    @Test
    void checkInOfCancelledEventTicketIsRejected() throws Exception {
        var orderId = paidOrder(user("USER"), 1);
        var code = jdbc.queryForObject("SELECT code FROM tickets WHERE order_id = ?", String.class, orderId);
        eventService.markCancelled(eventId, null, organizer);

        mockMvc.perform(post("/tickets/{code}/check-in", code).header("Authorization", bearer(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Event was cancelled"));
        assertThat(ticketStatuses(orderId)).containsOnly("VALID");
    }

    @Test
    void buyerRefundGoesThroughTheGateway() throws Exception {
        var buyer = user("USER");
        var orderId = paidOrder(buyer, 1);
        var paymentId = paymentId(orderId);

        mockMvc.perform(post("/orders/{id}/refund", orderId).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        verify(paymentGateway).refund(argThat(request -> request != null && paymentId.equals(request.paymentId())));
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
    }

    @Test
    void onlyOwnerOrAdminCanCancel() throws Exception {
        cancel(user("ORGANIZER"), null).andExpect(status().isForbidden());
        cancel(user("USER"), null).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT cancelled_at FROM events WHERE id = ?", Timestamp.class, eventId)).isNull();

        cancel(user("ADMIN"), null).andExpect(status().isOk());
    }

    @Test
    void alreadyCancelledEventReturnsConflict() throws Exception {
        cancel(organizer, null).andExpect(status().isOk());

        cancel(organizer, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Event is already cancelled"));
    }

    @Test
    void endedEventReturnsConflict() throws Exception {
        var past = Instant.now().minus(Duration.ofDays(2));
        var ended = insertEvent(organizer.getId(), past, past.plus(Duration.ofHours(3)), false);
        var startedWithoutEnd = insertEvent(organizer.getId(), Instant.now().minus(Duration.ofMinutes(5)), null, false);

        for (var id : List.of(ended, startedWithoutEnd)) {
            mockMvc.perform(post("/events/{id}/cancel", id).header("Authorization", bearer(organizer)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Event has already ended"));
        }
    }

    @Test
    void cancelledEventCannotBeEditedOrGetNewTicketTypes() throws Exception {
        cancel(organizer, null).andExpect(status().isOk());

        mockMvc.perform(post("/events/{id}/ticket-types", eventId)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Extra\",\"price\":10,\"quantityTotal\":10,\"maxPerOrder\":2}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/events/{id}", eventId)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo nome\"}"))
                .andExpect(status().isConflict());
    }

    private ResultActions cancel(User requester, String body) throws Exception {
        var request = post("/events/{id}/cancel", eventId).header("Authorization", bearer(requester));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private User user(String role) {
        return userRepository.findById(insertUser(role)).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private OrderCreationDTO order(int quantity) {
        return new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity)));
    }

    private UUID pendingOrder(User buyer, int quantity) {
        return orderService.create(order(quantity), null, buyer).id();
    }

    private UUID paidOrder(User buyer, int quantity) {
        var orderId = pendingOrder(buyer, quantity);
        paymentService.paySimulated(orderId, buyer);
        return orderId;
    }

    private UUID insertEvent(UUID organizerId, Instant startsAt, Instant endsAt, boolean featured) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, name, venue_name, address, city, state, starts_at, ends_at, organizer_id, active, featured)
                VALUES (?, ?, 'Arena', 'Rua A, 100', 'Recife', 'PE', ?, ?, ?, true, ?)
                """, id, "Show " + tag, Timestamp.from(startsAt), endsAt == null ? null : Timestamp.from(endsAt),
                organizerId, featured);
        return id;
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String paymentStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
    }

    private UUID paymentId(UUID orderId) {
        return jdbc.queryForObject("SELECT id FROM payments WHERE order_id = ?", UUID.class, orderId);
    }

    private List<String> ticketStatuses(UUID orderId) {
        return jdbc.queryForList("SELECT status FROM tickets WHERE order_id = ?", String.class, orderId);
    }
}
