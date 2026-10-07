package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TicketTypeUpdateIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID organizerId;
    private UUID eventId;
    private UUID ticketTypeId;

    // insertTicketType creates "VIP": price 80.00, quantityTotal 100, quantitySold 0, maxPerOrder 4
    @BeforeEach
    void setUp() {
        organizerId = insertUser("ORGANIZER");
        eventId = insertEvent(organizerId);
        ticketTypeId = insertTicketType(eventId, 100);
    }

    // --- editing ------------------------------------------------------------------------------------------------

    @Test
    void ownerEditsEveryField() throws Exception {
        update(organizerId, """
                {"name":"Pista Premium","description":"Área VIP","price":120.50,"quantityTotal":150,"maxPerOrder":6}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.name").value("Pista Premium"))
                .andExpect(jsonPath("$.description").value("Área VIP"))
                .andExpect(jsonPath("$.price").value(120.50))
                .andExpect(jsonPath("$.quantityTotal").value(150))
                .andExpect(jsonPath("$.quantitySold").value(0))
                .andExpect(jsonPath("$.available").value(150))
                .andExpect(jsonPath("$.maxPerOrder").value(6));

        var row = stored();
        assertThat(row.get("name")).isEqualTo("Pista Premium");
        assertThat(row.get("description")).isEqualTo("Área VIP");
        assertThat(row.get("price")).isEqualTo(new BigDecimal("120.50"));
        assertThat(row.get("quantity_total")).isEqualTo(150);
        assertThat(row.get("max_per_order")).isEqualTo(6);
    }

    @Test
    void adminCanEditAnyEvent() throws Exception {
        update(insertUser("ADMIN"), "{\"price\":95.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(95.00));
    }

    @Test
    void keepsFieldsThatWereNotSent() throws Exception {
        update(organizerId, "{\"price\":90.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP"))
                .andExpect(jsonPath("$.description").value("Test ticket type"))
                .andExpect(jsonPath("$.quantityTotal").value(100))
                .andExpect(jsonPath("$.maxPerOrder").value(4))
                .andExpect(jsonPath("$.price").value(90.00));

        update(organizerId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(90.00));
    }

    @Test
    void blankDescriptionRemovesIt() throws Exception {
        for (var blank : new String[]{"", "   "}) {
            jdbc.update("UPDATE ticket_types SET description = 'Lote' WHERE id = ?", ticketTypeId);

            update(organizerId, "{\"description\":\"" + blank + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").isEmpty());

            assertThat(stored().get("description")).isNull();
        }
    }

    @Test
    void persistsNameAndQuantityTotalChangedTogether() throws Exception {
        setSold(7);

        update(organizerId, "{\"name\":\"Pista\",\"quantityTotal\":60}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pista"))
                .andExpect(jsonPath("$.quantityTotal").value(60))
                .andExpect(jsonPath("$.quantitySold").value(7))
                .andExpect(jsonPath("$.available").value(53));

        var row = stored();
        assertThat(row.get("name")).isEqualTo("Pista");
        assertThat(row.get("quantity_total")).isEqualTo(60);
        assertThat(row.get("quantity_sold")).isEqualTo(7);
    }

    @Test
    void responseReflectsStockChangedOutsideTheRequest() throws Exception {
        // the lot was loaded before; a sale recorded in the database must show up in the response
        setSold(12);

        update(organizerId, "{\"name\":\"Pista\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityTotal").value(100))
                .andExpect(jsonPath("$.quantitySold").value(12))
                .andExpect(jsonPath("$.available").value(88));
    }

    // --- quantityTotal ------------------------------------------------------------------------------------------

    @Test
    void rejectsQuantityTotalBelowSoldAndReserved() throws Exception {
        setSold(37);

        update(organizerId, "{\"name\":\"Renomeado\",\"quantityTotal\":36}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quantityTotal cannot be lower than the 37 tickets already sold or reserved"));

        // the whole update is rolled back, including the name
        var row = stored();
        assertThat(row.get("quantity_total")).isEqualTo(100);
        assertThat(row.get("name")).isEqualTo("VIP");
    }

    @Test
    void acceptsQuantityTotalEqualToSold() throws Exception {
        setSold(37);

        update(organizerId, "{\"quantityTotal\":37}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityTotal").value(37))
                .andExpect(jsonPath("$.available").value(0));
    }

    // --- access and validation ----------------------------------------------------------------------------------

    @Test
    void onlyOwnerOrAdminCanEdit() throws Exception {
        update(insertUser("ORGANIZER"), "{\"price\":1.00}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not own this event"));
        update(insertUser("USER"), "{\"price\":1.00}").andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{e}/ticket-types/{t}", eventId, ticketTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1.00}"))
                .andExpect(status().isUnauthorized());

        assertThat(stored().get("price")).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void returnsNotFoundForUnknownLotOrEventMismatchOrInactiveEvent() throws Exception {
        perform(organizerId, eventId, UUID.randomUUID(), "{\"price\":1.00}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket type not found"));

        var otherEvent = insertEvent(organizerId);
        perform(organizerId, otherEvent, ticketTypeId, "{\"price\":1.00}")
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE events SET active = false WHERE id = ?", eventId);
        update(organizerId, "{\"price\":1.00}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found"));

        assertThat(stored().get("price")).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void validatesFields() throws Exception {
        var invalidBodies = Map.of(
                "price", "{\"price\":0}",
                "name", "{\"name\":\"   \"}",
                "maxPerOrder", "{\"maxPerOrder\":0}",
                "quantityTotal", "{\"quantityTotal\":0}",
                "description", "{\"description\":\"" + "a".repeat(501) + "\"}");
        for (var entry : invalidBodies.entrySet()) {
            update(organizerId, entry.getValue())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(entry.getKey()));
        }
    }

    // --- frozen prices and stock consistency --------------------------------------------------------------------

    @Test
    void priceChangeDoesNotAffectExistingOrders() throws Exception {
        var buyer = insertUser("USER");
        var orderId = createOrder(buyer, 2);
        mockMvc.perform(post("/orders/{id}/payment", orderId).header("Authorization", tokenFor(buyer)))
                .andExpect(status().is2xxSuccessful());

        update(organizerId, "{\"price\":120.00}").andExpect(status().isOk());

        mockMvc.perform(get("/orders/{id}", orderId).header("Authorization", tokenFor(buyer)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(80.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(160.00))
                .andExpect(jsonPath("$.totalAmount").value(160.00));

        mockMvc.perform(get("/events/{id}/dashboard", eventId).header("Authorization", tokenFor(organizerId)))
                .andExpect(jsonPath("$.totals.revenue").value(160.00))
                .andExpect(jsonPath("$.ticketTypes[0].price").value(120.00));

        var newOrder = createOrder(buyer, 1);
        mockMvc.perform(get("/orders/{id}", newOrder).header("Authorization", tokenFor(buyer)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(120.00));
    }

    @Test
    void editingTheLotNeverOverwritesConcurrentStockChanges() {
        var outer = new TransactionTemplate(transactionManager);
        var concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            var ticketType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
            assertThat(ticketType.getQuantitySold()).isZero();

            // a sale and a capacity change commit while the lot is loaded with the old stock
            concurrent.executeWithoutResult(inner -> {
                ticketTypeRepository.reserveStock(ticketTypeId, 5);
                ticketTypeRepository.changeQuantityTotal(ticketTypeId, 150);
            });

            ticketType.updateFrom(new TicketTypeUpdateDTO("Renomeado", null, null, null, null));
        });

        var row = stored();
        assertThat(row.get("name")).isEqualTo("Renomeado");
        assertThat(row.get("quantity_sold")).isEqualTo(5);
        assertThat(row.get("quantity_total")).isEqualTo(150);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private UUID createOrder(UUID buyer, int quantity) throws Exception {
        var body = mockMvc.perform(post("/orders")
                        .header("Authorization", tokenFor(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asString());
    }

    private ResultActions update(UUID userId, String body) throws Exception {
        return perform(userId, eventId, ticketTypeId, body);
    }

    private ResultActions perform(UUID userId, UUID event, UUID ticketType, String body) throws Exception {
        return mockMvc.perform(patch("/events/{e}/ticket-types/{t}", event, ticketType)
                .header("Authorization", tokenFor(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void setSold(int quantitySold) {
        jdbc.update("UPDATE ticket_types SET quantity_sold = ? WHERE id = ?", quantitySold, ticketTypeId);
    }

    private Map<String, Object> stored() {
        return jdbc.queryForMap("SELECT * FROM ticket_types WHERE id = ?", ticketTypeId);
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + tokenService.generateToken(userRepository.findById(userId).orElseThrow());
    }
}
