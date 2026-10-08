package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PayoutIntegrationTest extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private PayoutSettings payoutSettings;

    @MockitoBean
    private Clock clock;

    private User organizer;
    private User buyer;
    private Instant eventEnd;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenAnswer(invocation -> Instant.now());
        organizer = user("ORGANIZER");
        buyer = user("USER");
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        eventEnd = startsAt.plus(Duration.ofHours(4));
        eventId = insertEvent(organizer.getId(), startsAt, eventEnd);
    }

    @Test
    void paymentCreatesSaleCreditWithRoundedFeeAndNet() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "10.10");

        var orderId = paidOrder(ticketTypeId, 1);

        var order = orderRow(orderId);
        assertThat(order.get("total_amount")).isEqualTo(new BigDecimal("10.10"));
        assertThat(order.get("platform_fee_percent")).isEqualTo(new BigDecimal("5.00"));
        assertThat(order.get("platform_fee")).isEqualTo(new BigDecimal("0.51"));
        assertThat(order.get("net_amount")).isEqualTo(new BigDecimal("9.59"));
        assertThat(entries(orderId)).containsExactly(Map.entry("SALE_CREDIT", new BigDecimal("9.59")));
    }

    @Test
    void changingTheFeePercentDoesNotAffectExistingOrders() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "100.00");
        var before = orderService.create(order(ticketTypeId, 1), null, buyer).id();

        when(payoutSettings.platformFeePercent()).thenReturn(new BigDecimal("10"));
        paymentService.paySimulated(before, buyer);
        var after = paidOrder(ticketTypeId, 1);

        assertThat(orderRow(before).get("platform_fee")).isEqualTo(new BigDecimal("5.00"));
        assertThat(entries(before)).containsExactly(Map.entry("SALE_CREDIT", new BigDecimal("95.00")));
        assertThat(orderRow(after).get("platform_fee")).isEqualTo(new BigDecimal("10.00"));
        assertThat(entries(after)).containsExactly(Map.entry("SALE_CREDIT", new BigDecimal("90.00")));
    }

    @Test
    void buyerRefundCreatesRefundDebit() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "50.00");
        var orderId = paidOrder(ticketTypeId, 2);

        mockMvc.perform(post("/orders/" + orderId + "/refund").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk());

        assertThat(entries(orderId)).containsExactly(
                Map.entry("SALE_CREDIT", new BigDecimal("95.00")),
                Map.entry("REFUND_DEBIT", new BigDecimal("-95.00")));
        assertThat(balance(organizer).get("total").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void eventCancellationCreatesRefundDebitAndHoldsTheBalance() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "40.00");
        var refunded = paidOrder(ticketTypeId, 1);
        var usedTicketOrder = paidOrder(ticketTypeId, 1);
        jdbc.update("UPDATE tickets SET status = 'USED', used_at = NOW() WHERE order_id = ?", usedTicketOrder);

        mockMvc.perform(post("/events/" + eventId + "/cancel")
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedOrders").value(1))
                .andExpect(jsonPath("$.requiresManualAction").value(1));

        assertThat(entries(refunded)).containsExactly(
                Map.entry("SALE_CREDIT", new BigDecimal("38.00")),
                Map.entry("REFUND_DEBIT", new BigDecimal("-38.00")));
        assertThat(entries(usedTicketOrder)).containsExactly(Map.entry("SALE_CREDIT", new BigDecimal("38.00")));

        when(clock.instant()).thenReturn(eventEnd.plus(Duration.ofDays(30)));
        var balance = balance(organizer);
        assertThat(balance.get("pending").decimalValue()).isEqualByComparingTo("0");
        assertThat(balance.get("available").decimalValue()).isEqualByComparingTo("0");
        assertThat(balance.get("held").decimalValue()).isEqualByComparingTo("38.00");
        assertThat(balance.get("total").decimalValue()).isEqualByComparingTo("38.00");
    }

    @Test
    void duplicateEntriesAreRejectedByTheDatabase() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "20.00");
        var orderId = paidOrder(ticketTypeId, 1);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx ->
                ledgerService.recordSale(orderRepository.findById(orderId).orElseThrow())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO organizer_ledger_entries (organizer_id, event_id, order_id, type, amount)
                VALUES (?, ?, ?, 'SALE_CREDIT', 1.00)
                """, organizer.getId(), eventId, orderId))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(entries(orderId)).hasSize(1);
    }

    @Test
    void ledgerEntriesCannotBeChangedOrDeleted() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "20.00");
        var orderId = paidOrder(ticketTypeId, 1);

        assertThatThrownBy(() -> jdbc.update("UPDATE organizer_ledger_entries SET amount = 1 WHERE order_id = ?", orderId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM organizer_ledger_entries WHERE order_id = ?", orderId))
                .hasMessageContaining("append-only");
        assertThat(entries(orderId)).containsExactly(Map.entry("SALE_CREDIT", new BigDecimal("19.00")));
    }

    @Test
    void pendingBalanceBecomesAvailableAfterEndPlusReleaseDelay() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "100.00");
        paidOrder(ticketTypeId, 1);
        var releaseAt = eventEnd.plus(Duration.ofDays(2));

        when(clock.instant()).thenReturn(releaseAt.minus(Duration.ofMinutes(1)));
        var before = balance(organizer);
        assertThat(before.get("pending").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(before.get("available").decimalValue()).isEqualByComparingTo("0");
        assertThat(before.get("releaseDelayDays").asInt()).isEqualTo(2);

        when(clock.instant()).thenReturn(releaseAt);
        var after = balance(organizer);
        assertThat(after.get("pending").decimalValue()).isEqualByComparingTo("0");
        assertThat(after.get("available").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(after.get("held").decimalValue()).isEqualByComparingTo("0");
        assertThat(after.get("total").decimalValue()).isEqualByComparingTo("95.00");
    }

    @Test
    void releaseUsesStartWhenEventHasNoEnd() throws Exception {
        var startsAt = Instant.now().plus(Duration.ofDays(10));
        var noEndEvent = insertEvent(organizer.getId(), startsAt, null);
        paidOrder(insertTicketType(noEndEvent, "10.00"), 1);

        when(clock.instant()).thenReturn(startsAt.plus(Duration.ofDays(2)));

        assertThat(balance(organizer).get("available").decimalValue()).isEqualByComparingTo("9.50");
    }

    @Test
    void freeOrderCreatesNoLedgerEntry() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "0.00");

        var orderId = paidOrder(ticketTypeId, 2);

        var order = orderRow(orderId);
        assertThat(order.get("platform_fee")).isEqualTo(new BigDecimal("0.00"));
        assertThat(order.get("net_amount")).isEqualTo(new BigDecimal("0.00"));
        assertThat(entries(orderId)).isEmpty();
    }

    @Test
    void organizerCannotSeeAnotherOrganizersBalanceOrLedger() throws Exception {
        paidOrder(insertTicketType(eventId, "30.00"), 1);
        var other = user("ORGANIZER");

        var balance = balance(other);
        assertThat(balance.get("total").decimalValue()).isEqualByComparingTo("0");
        mockMvc.perform(get("/organizer/ledger").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/organizer/ledger").param("eventId", eventId.toString())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void onlyOrganizersCanReadBalanceAndLedger() throws Exception {
        mockMvc.perform(get("/organizer/balance").header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/organizer/ledger").header("Authorization", bearer(user("ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/organizer/balance")).andExpect(status().isUnauthorized());
    }

    @Test
    void ledgerSumMatchesBalanceAndListsNewestFirst() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "25.00");
        var first = paidOrder(ticketTypeId, 1);
        var second = paidOrder(ticketTypeId, 2);
        orderService.refund(first, buyer);
        var otherEvent = insertEvent(organizer.getId(), Instant.now().plus(Duration.ofDays(40)), null);
        var third = paidOrder(insertTicketType(otherEvent, "12.34"), 1);

        var ledger = ledger(organizer, Map.of("size", "50"));
        var content = ledger.get("content");
        assertThat(content).hasSize(4);
        var sum = BigDecimal.ZERO;
        for (var entry : content) {
            sum = sum.add(entry.get("amount").decimalValue());
        }
        var balance = balance(organizer);
        assertThat(sum).isEqualByComparingTo(balance.get("total").decimalValue());
        assertThat(balance.get("pending").decimalValue()
                .add(balance.get("available").decimalValue())
                .add(balance.get("held").decimalValue()))
                .isEqualByComparingTo(balance.get("total").decimalValue());
        assertThat(content.get(0).get("orderId").asString()).isEqualTo(third.toString());
        assertThat(content.get(0).get("eventName").asString()).isEqualTo("Evento " + otherEvent);
        assertThat(content.get(1).get("type").asString()).isEqualTo("REFUND_DEBIT");
        assertThat(content.get(1).get("orderId").asString()).isEqualTo(first.toString());

        var filtered = ledger(organizer, Map.of("eventId", eventId.toString())).get("content");
        assertThat(filtered).hasSize(3);
        var filteredOrders = new ArrayList<String>();
        for (var entry : filtered) {
            filteredOrders.add(entry.get("orderId").asString());
        }
        assertThat(filteredOrders).containsOnly(first.toString(), second.toString());
    }

    @Test
    void ledgerFiltersByPeriodInConfiguredTimeZone() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "10.00");
        when(clock.instant()).thenReturn(Instant.parse("2026-03-10T02:30:00Z"));
        var lateNight = paidOrder(ticketTypeId, 1);
        when(clock.instant()).thenReturn(Instant.parse("2026-03-10T03:30:00Z"));
        var nextDay = paidOrder(ticketTypeId, 1);

        var ninth = ledger(organizer, Map.of("from", "2026-03-09", "to", "2026-03-09")).get("content");
        assertThat(ninth).hasSize(1);
        assertThat(ninth.get(0).get("orderId").asString()).isEqualTo(lateNight.toString());
        var tenth = ledger(organizer, Map.of("from", "2026-03-10")).get("content");
        assertThat(tenth).hasSize(1);
        assertThat(tenth.get(0).get("orderId").asString()).isEqualTo(nextDay.toString());

        mockMvc.perform(get("/organizer/ledger").param("from", "2026-03-11").param("to", "2026-03-10")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardsShowGrossFeeAndNet() throws Exception {
        var ticketTypeId = insertTicketType(eventId, "10.10");
        paidOrder(ticketTypeId, 1);
        paidOrder(ticketTypeId, 2);

        mockMvc.perform(get("/events/" + eventId + "/dashboard").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value(30.30))
                .andExpect(jsonPath("$.totals.platformFee").value(1.52))
                .andExpect(jsonPath("$.totals.netRevenue").value(28.78));
        mockMvc.perform(get("/events/" + eventId + "/orders").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].total").value(20.20))
                .andExpect(jsonPath("$.content[0].platformFee").value(1.01))
                .andExpect(jsonPath("$.content[0].netAmount").value(19.19));
        mockMvc.perform(get("/organizer/dashboard").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value(30.30))
                .andExpect(jsonPath("$.totals.platformFee").value(1.52))
                .andExpect(jsonPath("$.totals.netRevenue").value(28.78))
                .andExpect(jsonPath("$.events.content[0].platformFee").value(1.52))
                .andExpect(jsonPath("$.events.content[0].netRevenue").value(28.78));
    }

    private UUID paidOrder(UUID ticketTypeId, int quantity) {
        var orderId = orderService.create(order(ticketTypeId, quantity), null, buyer).id();
        paymentService.paySimulated(orderId, buyer);
        return orderId;
    }

    private static OrderCreationDTO order(UUID ticketTypeId, int quantity) {
        return new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity)));
    }

    private JsonNode balance(User user) throws Exception {
        var body = mockMvc.perform(get("/organizer/balance").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private JsonNode ledger(User user, Map<String, String> params) throws Exception {
        var request = get("/organizer/ledger").header("Authorization", bearer(user));
        params.forEach(request::param);
        ResultActions result = mockMvc.perform(request).andExpect(status().isOk());
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Map<String, Object> orderRow(UUID orderId) {
        return jdbc.queryForMap(
                "SELECT total_amount, platform_fee_percent, platform_fee, net_amount FROM orders WHERE id = ?", orderId);
    }

    private List<Map.Entry<String, BigDecimal>> entries(UUID orderId) {
        return jdbc.query("""
                SELECT type, amount FROM organizer_ledger_entries
                 WHERE order_id = ?
                 ORDER BY CASE type WHEN 'SALE_CREDIT' THEN 0 ELSE 1 END
                """, (rs, i) -> Map.entry(rs.getString("type"), rs.getBigDecimal("amount")), orderId);
    }

    private User user(String role) {
        return userRepository.findById(insertUser(role)).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }

    private UUID insertEvent(UUID organizerId, Instant startsAt, Instant endsAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, name, description, venue_name, address, city, state,
                                    starts_at, ends_at, organizer_id, active, created_at)
                VALUES (?, ?, 'Evento de teste', 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ', ?, ?, ?, true, NOW())
                """, id, "Evento " + id, Timestamp.from(startsAt), endsAt == null ? null : Timestamp.from(endsAt),
                organizerId);
        return id;
    }

    private UUID insertTicketType(UUID eventId, String price) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price,
                                          quantity_total, quantity_sold, max_per_order, active, created_at)
                VALUES (?, ?, ?, 'Lote de teste', ?, 100, 0, 10, true, NOW())
                """, id, eventId, "Lote " + id.toString().substring(0, 8), new BigDecimal(price));
        return id;
    }
}
