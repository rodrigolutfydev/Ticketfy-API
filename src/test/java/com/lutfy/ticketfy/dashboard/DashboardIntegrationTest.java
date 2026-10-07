package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DashboardIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID organizerId;
    private UUID maria;
    private UUID joao;
    private UUID event1;
    private UUID event2;
    private UUID event3;
    private UUID event4;
    private UUID pista;
    private UUID vip;
    private UUID arquibancada;
    private UUID o1, o2, o3, o4, o5, o6;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        organizerId = insertUser("ORGANIZER");
        var otherOrganizer = insertUser("ORGANIZER");
        maria = insertBuyer("Maria Silva", "maria." + suffix + "@mail.com");
        joao = insertBuyer("João Souza", "joao." + suffix + "@mail.com");

        event1 = insertEvent(organizerId, "Show de Rock", 10, true);
        event2 = insertEvent(organizerId, "Festival de Jazz", 20, true);
        event3 = insertEvent(otherOrganizer, "Peça de Teatro", 15, true);
        event4 = insertEvent(organizerId, "Evento Desativado", 5, false);

        pista = insertTicketType(event1, "Pista", "80.00", 100, 3, 1);
        vip = insertTicketType(event1, "VIP", "200.00", 20, 3, 2);
        arquibancada = insertTicketType(event2, "Arquibancada", "50.00", 50, 2, 1);
        var inactiveLot = insertTicketType(event4, "Lote único", "10.00", 10, 1, 1);

        o1 = insertOrder(maria, "PAID", "2026-09-01T02:20:00Z", "360.00");
        var o1Pista = insertItem(o1, pista, "80.00", 2);
        var o1Vip = insertItem(o1, vip, "200.00", 1);
        insertPayment(o1, "360.00", "2026-09-01T02:30:00Z");
        insertTickets(o1, o1Pista, pista, maria, "USED", 1);
        insertTickets(o1, o1Pista, pista, maria, "VALID", 1);
        insertTickets(o1, o1Vip, vip, maria, "VALID", 1);

        o2 = insertOrder(joao, "PAID", "2026-09-02T14:50:00Z", "170.00");
        var o2Pista = insertItem(o2, pista, "70.00", 1);
        var o2Arq = insertItem(o2, arquibancada, "50.00", 2);
        insertPayment(o2, "170.00", "2026-09-02T15:00:00Z");
        insertTickets(o2, o2Pista, pista, joao, "VALID", 1);
        insertTickets(o2, o2Arq, arquibancada, joao, "VALID", 2);

        o3 = insertOrder(joao, "PENDING", "2026-09-03T10:00:00Z", "400.00");
        insertItem(o3, vip, "200.00", 2);

        o4 = insertOrder(maria, "CANCELLED", "2026-09-03T11:00:00Z", "80.00");
        insertItem(o4, pista, "80.00", 1);

        o5 = insertOrder(joao, "EXPIRED", "2026-09-03T12:00:00Z", "240.00");
        insertItem(o5, pista, "80.00", 3);

        o6 = insertOrder(maria, "REFUNDED", "2026-09-01T10:00:00Z", "200.00");
        var o6Vip = insertItem(o6, vip, "200.00", 1);
        insertPayment(o6, "200.00", "2026-09-01T10:05:00Z");
        insertTickets(o6, o6Vip, vip, maria, "CANCELLED", 1);

        var o7 = insertOrder(joao, "PAID", "2026-09-02T09:00:00Z", "10.00");
        var o7Item = insertItem(o7, inactiveLot, "10.00", 1);
        insertPayment(o7, "10.00", "2026-09-02T09:05:00Z");
        insertTickets(o7, o7Item, inactiveLot, joao, "VALID", 1);
    }

    @Test
    void ownerAndAdminCanSeeTheDashboardButNotOthers() throws Exception {
        var routes = new String[]{"/events/%s/dashboard", "/events/%s/orders", "/events/%s/ticket-types/manage"};
        for (var route : routes) {
            var path = route.formatted(event1);
            mockMvc.perform(get(path).header("Authorization", tokenFor(organizerId))).andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", tokenFor(insertUser("ADMIN")))).andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", tokenFor(insertUser("ORGANIZER"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("You do not own this event"));
            mockMvc.perform(get(path).header("Authorization", tokenFor(maria))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(route.formatted(UUID.randomUUID())).header("Authorization", tokenFor(organizerId)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(route.formatted(event4)).header("Authorization", tokenFor(organizerId)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/organizer/dashboard").header("Authorization", tokenFor(maria)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/organizer/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void summarizesOnlyPaidOrdersAndOnlyItemsOfTheEvent() throws Exception {
        dashboard(event1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event1.toString()))
                .andExpect(jsonPath("$.eventName").value("Show de Rock"))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.totals.ticketsSold").value(4))
                .andExpect(jsonPath("$.totals.ticketsReserved").value(2))
                .andExpect(jsonPath("$.totals.capacity").value(120))
                .andExpect(jsonPath("$.totals.remaining").value(114))
                .andExpect(jsonPath("$.totals.percentSold").value(3.33))
                .andExpect(jsonPath("$.totals.revenue").value(430.00))
                .andExpect(jsonPath("$.totals.paidOrders").value(2))
                .andExpect(jsonPath("$.totals.averageOrderValue").value(215.00))
                .andExpect(jsonPath("$.totals.averageTicketPrice").value(107.50));
    }

    @Test
    void countsEveryStatusInFixedOrder() throws Exception {
        dashboard(event1)
                .andExpect(jsonPath("$.ordersByStatus[*].status")
                        .value(contains("PENDING", "PAID", "CANCELLED", "EXPIRED", "REFUNDED")))
                .andExpect(jsonPath("$.ordersByStatus[*].orders").value(contains(1, 2, 1, 1, 1)))
                .andExpect(jsonPath("$.ordersByStatus[*].tickets").value(contains(2, 4, 1, 3, 1)));
    }

    @Test
    void reportsCheckInsOverIssuedTickets() throws Exception {
        dashboard(event1)
                .andExpect(jsonPath("$.checkIn.checkedIn").value(1))
                .andExpect(jsonPath("$.checkIn.issued").value(4))
                .andExpect(jsonPath("$.checkIn.attendanceRate").value(25.00));
    }

    @Test
    void breaksDownSalesPerTicketTypeUsingFrozenPrices() throws Exception {
        jdbc.update("UPDATE ticket_types SET price = 999.00 WHERE id = ?", pista);

        dashboard(event1)
                .andExpect(jsonPath("$.ticketTypes", hasSize(2)))
                .andExpect(jsonPath("$.ticketTypes[0].id").value(pista.toString()))
                .andExpect(jsonPath("$.ticketTypes[0].name").value("Pista"))
                .andExpect(jsonPath("$.ticketTypes[0].price").value(999.00))
                .andExpect(jsonPath("$.ticketTypes[0].quantityTotal").value(100))
                .andExpect(jsonPath("$.ticketTypes[0].sold").value(3))
                .andExpect(jsonPath("$.ticketTypes[0].reserved").value(0))
                .andExpect(jsonPath("$.ticketTypes[0].remaining").value(97))
                .andExpect(jsonPath("$.ticketTypes[0].revenue").value(230.00))
                .andExpect(jsonPath("$.ticketTypes[0].percentSold").value(3.00))
                .andExpect(jsonPath("$.ticketTypes[1].name").value("VIP"))
                .andExpect(jsonPath("$.ticketTypes[1].sold").value(1))
                .andExpect(jsonPath("$.ticketTypes[1].reserved").value(2))
                .andExpect(jsonPath("$.ticketTypes[1].remaining").value(17))
                .andExpect(jsonPath("$.ticketTypes[1].revenue").value(200.00))
                .andExpect(jsonPath("$.ticketTypes[1].percentSold").value(5.00))
                .andExpect(jsonPath("$.totals.revenue").value(430.00));
    }

    @Test
    void buildsContinuousDailySeriesInConfiguredTimeZone() throws Exception {
        dashboard(event1)
                .andExpect(jsonPath("$.dailySales", hasSize(3)))
                .andExpect(jsonPath("$.dailySales[*].date").value(contains("2026-08-31", "2026-09-01", "2026-09-02")))
                .andExpect(jsonPath("$.dailySales[*].tickets").value(contains(3, 0, 1)))
                .andExpect(jsonPath("$.dailySales[*].revenue").value(contains(360.00, 0.00, 70.00)));
    }

    @Test
    void mixedOrderOnlyCountsItsItemsInTheOtherEvent() throws Exception {
        dashboard(event2)
                .andExpect(jsonPath("$.totals.ticketsSold").value(2))
                .andExpect(jsonPath("$.totals.revenue").value(100.00))
                .andExpect(jsonPath("$.totals.capacity").value(50))
                .andExpect(jsonPath("$.totals.percentSold").value(4.00))
                .andExpect(jsonPath("$.totals.averageOrderValue").value(100.00))
                .andExpect(jsonPath("$.totals.averageTicketPrice").value(50.00))
                .andExpect(jsonPath("$.ordersByStatus[*].orders").value(contains(0, 1, 0, 0, 0)))
                .andExpect(jsonPath("$.checkIn.checkedIn").value(0))
                .andExpect(jsonPath("$.checkIn.issued").value(2))
                .andExpect(jsonPath("$.checkIn.attendanceRate").value(0.00))
                .andExpect(jsonPath("$.dailySales[*].date").value(contains("2026-09-02")));
    }

    @Test
    void returnsNullRatesAndEmptySeriesWithoutSales() throws Exception {
        mockMvc.perform(get("/events/{id}/dashboard", event3).header("Authorization", tokenFor(insertUser("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(0))
                .andExpect(jsonPath("$.totals.capacity").value(0))
                .andExpect(jsonPath("$.totals.revenue").value(0.00))
                .andExpect(jsonPath("$.totals.percentSold").isEmpty())
                .andExpect(jsonPath("$.totals.averageOrderValue").isEmpty())
                .andExpect(jsonPath("$.totals.averageTicketPrice").isEmpty())
                .andExpect(jsonPath("$.ordersByStatus", hasSize(5)))
                .andExpect(jsonPath("$.checkIn.attendanceRate").isEmpty())
                .andExpect(jsonPath("$.ticketTypes", hasSize(0)))
                .andExpect(jsonPath("$.dailySales", hasSize(0)));
    }

    @Test
    void serializesMoneyLikePrices() throws Exception {
        var body = dashboard(event1).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"revenue\":430.00", "\"price\":80.00", "\"averageTicketPrice\":107.50",
                "\"percentSold\":3.33");
    }

    @Test
    void listsEventOrdersNewestFirstWithOnlyTheEventItems() throws Exception {
        orders(event1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.content[*].orderId").value(contains(
                        o5.toString(), o4.toString(), o3.toString(), o2.toString(), o6.toString(), o1.toString())))
                .andExpect(jsonPath("$.content[3].status").value("PAID"))
                .andExpect(jsonPath("$.content[3].createdAt").value("2026-09-02T14:50:00Z"))
                .andExpect(jsonPath("$.content[3].paidAt").value("2026-09-02T15:00:00Z"))
                .andExpect(jsonPath("$.content[3].buyer.name").value("João Souza"))
                .andExpect(jsonPath("$.content[3].buyer.email").value("joao." + suffix + "@mail.com"))
                .andExpect(jsonPath("$.content[3].items", hasSize(1)))
                .andExpect(jsonPath("$.content[3].items[0].ticketTypeId").value(pista.toString()))
                .andExpect(jsonPath("$.content[3].items[0].ticketTypeName").value("Pista"))
                .andExpect(jsonPath("$.content[3].items[0].quantity").value(1))
                .andExpect(jsonPath("$.content[3].items[0].unitPrice").value(70.00))
                .andExpect(jsonPath("$.content[3].items[0].subtotal").value(70.00))
                .andExpect(jsonPath("$.content[3].total").value(70.00))
                .andExpect(jsonPath("$.content[2].paidAt").isEmpty())
                .andExpect(jsonPath("$.content[5].items", hasSize(2)))
                .andExpect(jsonPath("$.content[5].total").value(360.00));

        orders(event2, "")
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(o2.toString()))
                .andExpect(jsonPath("$.content[0].items[0].ticketTypeId").value(arquibancada.toString()))
                .andExpect(jsonPath("$.content[0].total").value(100.00));
    }

    @Test
    void exposesOnlyNameAndEmailOfTheBuyer() throws Exception {
        var body = orders(event1, "").andReturn().getResponse().getContentAsString();
        var order = objectMapper.readTree(body).get("content").get(0);

        assertThat(order.propertyNames()).containsExactlyInAnyOrder(
                "orderId", "status", "createdAt", "paidAt", "buyer", "items", "total",
                "platformFee", "netAmount");
        assertThat(order.get("buyer").propertyNames()).containsExactlyInAnyOrder("name", "email");
        assertThat(body).doesNotContain(maria.toString(), joao.toString(), "not-used");
    }

    @Test
    void filtersOrdersByStatusAndBuyer() throws Exception {
        orders(event1, "?status=PAID")
                .andExpect(jsonPath("$.content[*].orderId").value(contains(o2.toString(), o1.toString())));
        orders(event1, "?q=MARIA")
                .andExpect(jsonPath("$.content[*].orderId").value(contains(o4.toString(), o6.toString(), o1.toString())));
        orders(event1, "?q=joao." + suffix)
                .andExpect(jsonPath("$.content[*].orderId").value(containsInAnyOrder(o5.toString(), o3.toString(), o2.toString())));
        orders(event1, "?status=PENDING&q=joao")
                .andExpect(jsonPath("$.content[*].orderId").value(contains(o3.toString())));
        orders(event1, "?q=%25")
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void rejectsInvalidStatusFilter() throws Exception {
        orders(event1, "?status=FOO").andExpect(status().isBadRequest());
    }

    @Test
    void paginatesOrdersLikeOtherListings() throws Exception {
        orders(event1, "?page=1&size=2")
                .andExpect(jsonPath("$.content[*].orderId").value(contains(o3.toString(), o2.toString())))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        orders(event1, "?size=500").andExpect(jsonPath("$.page.size").value(100));

        var events = objectMapper.readTree(mockMvc.perform(get("/events")).andReturn().getResponse().getContentAsString());
        var orders = objectMapper.readTree(orders(event1, "").andReturn().getResponse().getContentAsString());
        assertThat(orders.propertyNames()).containsExactlyInAnyOrderElementsOf(events.propertyNames());
        assertThat(orders.get("page").propertyNames()).containsExactlyInAnyOrderElementsOf(events.get("page").propertyNames());
    }

    @Test
    void listsTicketTypesWithInternalNumbersForManagement() throws Exception {
        mockMvc.perform(get("/events/{id}/ticket-types/manage", event1).header("Authorization", tokenFor(organizerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(pista.toString()))
                .andExpect(jsonPath("$[0].name").value("Pista"))
                .andExpect(jsonPath("$[0].description").value("Lote Pista"))
                .andExpect(jsonPath("$[0].price").value(80.00))
                .andExpect(jsonPath("$[0].maxPerOrder").value(4))
                .andExpect(jsonPath("$[0].quantityTotal").value(100))
                .andExpect(jsonPath("$[0].quantitySold").value(3))
                .andExpect(jsonPath("$[0].available").value(97))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("VIP"));
    }

    @Test
    void summarizesAllActiveEventsOfTheOrganizer() throws Exception {
        mockMvc.perform(get("/organizer/dashboard").header("Authorization", tokenFor(organizerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.events").value(2))
                .andExpect(jsonPath("$.totals.ticketsSold").value(6))
                .andExpect(jsonPath("$.totals.revenue").value(530.00))
                .andExpect(jsonPath("$.events.page.totalElements").value(2))
                .andExpect(jsonPath("$.events.content[*].eventId").value(contains(event1.toString(), event2.toString())))
                .andExpect(jsonPath("$.events.content[0].name").value("Show de Rock"))
                .andExpect(jsonPath("$.events.content[0].startsAt").isNotEmpty())
                .andExpect(jsonPath("$.events.content[0].imageUrl").value("https://cdn.example.com/show-de-rock.jpg"))
                .andExpect(jsonPath("$.events.content[0].ticketsSold").value(4))
                .andExpect(jsonPath("$.events.content[0].capacity").value(120))
                .andExpect(jsonPath("$.events.content[0].percentSold").value(3.33))
                .andExpect(jsonPath("$.events.content[0].revenue").value(430.00))
                .andExpect(jsonPath("$.events.content[1].ticketsSold").value(2))
                .andExpect(jsonPath("$.events.content[1].revenue").value(100.00));
    }

    @Test
    void organizerTotalsCoverAllPagesAndOnlyTheirOwnEvents() throws Exception {
        mockMvc.perform(get("/organizer/dashboard?size=1&page=1").header("Authorization", tokenFor(organizerId)))
                .andExpect(jsonPath("$.totals.events").value(2))
                .andExpect(jsonPath("$.totals.revenue").value(530.00))
                .andExpect(jsonPath("$.events.content[*].eventId").value(contains(event2.toString())))
                .andExpect(jsonPath("$.events.page.totalPages").value(2));

        var other = jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID.class, event3);
        mockMvc.perform(get("/organizer/dashboard").header("Authorization", tokenFor(other)))
                .andExpect(jsonPath("$.totals.events").value(1))
                .andExpect(jsonPath("$.totals.ticketsSold").value(0))
                .andExpect(jsonPath("$.totals.revenue").value(0.00))
                .andExpect(jsonPath("$.events.content[0].eventId").value(event3.toString()))
                .andExpect(jsonPath("$.events.content[0].capacity").value(0))
                .andExpect(jsonPath("$.events.content[0].percentSold").isEmpty());

        mockMvc.perform(get("/organizer/dashboard").header("Authorization", tokenFor(insertUser("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.events").value(0))
                .andExpect(jsonPath("$.events.content", hasSize(0)));
    }

    private ResultActions dashboard(UUID eventId) throws Exception {
        return mockMvc.perform(get("/events/{id}/dashboard", eventId).header("Authorization", tokenFor(organizerId)));
    }

    private ResultActions orders(UUID eventId, String query) throws Exception {
        return mockMvc.perform(get("/events/" + eventId + "/orders" + query).header("Authorization", tokenFor(organizerId)));
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }

    private UUID insertBuyer(String name, String email) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'USER')",
                id, name, email, "not-used");
        return id;
    }

    private UUID insertEvent(UUID organizer, String name, int daysFromNow, boolean active) {
        var id = UUID.randomUUID();
        var startsAt = Instant.now().plus(Duration.ofDays(daysFromNow));
        var slug = name.toLowerCase().replace(' ', '-');
        jdbc.update("""
                INSERT INTO events (id, name, image_url, venue_name, address, city, state, starts_at, organizer_id, active)
                VALUES (?, ?, ?, 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ', ?, ?, ?)
                """, id, name, "https://cdn.example.com/" + slug + ".jpg", Timestamp.from(startsAt), organizer, active);
        return id;
    }

    private UUID insertTicketType(UUID eventId, String name, String price, int total, int sold, int createdOrder) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price, quantity_total, quantity_sold,
                                          max_per_order, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 4, true, NOW() - make_interval(mins => ?))
                """, id, eventId, name, "Lote " + name, new BigDecimal(price), total, sold, 10 - createdOrder);
        return id;
    }

    private UUID insertOrder(UUID userId, String status, String createdAt, String total) {
        var id = UUID.randomUUID();
        var created = Instant.parse(createdAt);
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, total_amount, platform_fee_percent, platform_fee,
                                    net_amount, expires_at, created_at)
                VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?)
                """, id, userId, status, new BigDecimal(total), new BigDecimal(total),
                Timestamp.from(created.plus(Duration.ofMinutes(15))), Timestamp.from(created));
        return id;
    }

    private UUID insertItem(UUID orderId, UUID ticketTypeId, String unitPrice, int quantity) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO order_items (id, order_id, ticket_type_id, unit_price, quantity) VALUES (?, ?, ?, ?, ?)",
                id, orderId, ticketTypeId, new BigDecimal(unitPrice), quantity);
        return id;
    }

    private void insertPayment(UUID orderId, String amount, String approvedAt) {
        jdbc.update("""
                INSERT INTO payments (order_id, status, method, amount, approved_at)
                VALUES (?, 'APPROVED', 'SIMULATED', ?, ?)
                """, orderId, new BigDecimal(amount), Timestamp.from(Instant.parse(approvedAt)));
    }

    private void insertTickets(UUID orderId, UUID itemId, UUID ticketTypeId, UUID ownerId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO tickets (code, order_id, order_item_id, ticket_type_id, owner_id, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString().replace("-", ""), orderId, itemId, ticketTypeId, ownerId, status);
        }
    }
}
