package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventSummaryPricingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private String tag;
    private UUID organizer;

    @BeforeEach
    void setUp() {
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        organizer = insertUser("ORGANIZER");
    }

    @Test
    void minPriceIgnoresSoldOutAndInactiveTicketTypes() throws Exception {
        var event = insertEvent("Show " + tag, "Recife " + tag, false);
        insertTicketType(event, "Lote 1", "50.00", 100, 100, true);
        insertTicketType(event, "Inativo", "30.00", 100, 0, false);
        insertTicketType(event, "Lote 2", "80.00", 100, 10, true);
        insertTicketType(event, "VIP", "150.00", 100, 0, true);

        search("q", tag)
                .andExpect(jsonPath("$.content[0].id").value(event.toString()))
                .andExpect(jsonPath("$.content[0].minPrice").value(80.00))
                .andExpect(jsonPath("$.content[0].soldOut").value(false));
    }

    @Test
    void freeTicketTypeHasZeroMinPrice() throws Exception {
        var event = insertEvent("Show " + tag, "Recife " + tag, false);
        insertTicketType(event, "Gratuito", "0.00", 100, 0, true);
        insertTicketType(event, "Pista", "40.00", 100, 0, true);

        search("q", tag)
                .andExpect(jsonPath("$.content[0].minPrice").value(0.00))
                .andExpect(jsonPath("$.content[0].soldOut").value(false));
    }

    @Test
    void eventWithAllTicketTypesSoldOutHasNoMinPrice() throws Exception {
        var event = insertEvent("Show " + tag, "Recife " + tag, false);
        insertTicketType(event, "Lote 1", "50.00", 100, 100, true);
        insertTicketType(event, "Lote 2", "80.00", 20, 20, true);
        insertTicketType(event, "Inativo", "30.00", 100, 0, false);

        search("q", tag)
                .andExpect(jsonPath("$.content[0].id").value(event.toString()))
                .andExpect(jsonPath("$.content[0].minPrice").isEmpty())
                .andExpect(jsonPath("$.content[0].soldOut").value(true));
    }

    @Test
    void eventWithoutActiveTicketTypesIsNotSoldOut() throws Exception {
        var noTicketTypes = insertEvent("Sem lotes " + tag, "Recife " + tag, false);
        var onlyInactive = insertEvent("Lotes inativos " + tag, "Recife " + tag, false);
        insertTicketType(onlyInactive, "Inativo", "30.00", 100, 100, false);

        search("q", tag, "sort", "name")
                .andExpect(jsonPath("$.content[0].id").value(onlyInactive.toString()))
                .andExpect(jsonPath("$.content[0].minPrice").isEmpty())
                .andExpect(jsonPath("$.content[0].soldOut").value(false))
                .andExpect(jsonPath("$.content[1].id").value(noTicketTypes.toString()))
                .andExpect(jsonPath("$.content[1].minPrice").isEmpty())
                .andExpect(jsonPath("$.content[1].soldOut").value(false));
    }

    @Test
    void soldOutFilterKeepsEventsWithStockOrWithoutTicketTypes() throws Exception {
        var available = insertEvent("Disponivel " + tag, "Recife " + tag, false);
        insertTicketType(available, "Lote 1", "50.00", 100, 100, true);
        insertTicketType(available, "Lote 2", "80.00", 100, 0, true);
        var soldOut = insertEvent("Esgotado " + tag, "Recife " + tag, false);
        insertTicketType(soldOut, "Lote 1", "50.00", 100, 100, true);
        var noTicketTypes = insertEvent("Sem lotes " + tag, "Recife " + tag, false);

        search("q", tag, "soldOut", "false")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        available.toString(), noTicketTypes.toString())))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        search("q", tag, "soldOut", "true")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(soldOut.toString())))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void soldOutFilterKeepsPaginationConsistent() throws Exception {
        for (int i = 0; i < 3; i++) {
            var soldOut = insertEvent("Esgotado " + i + " " + tag, "Recife " + tag, false);
            insertTicketType(soldOut, "Lote", "50.00", 10, 10, true);
        }
        for (int i = 0; i < 3; i++) {
            var available = insertEvent("Disponivel " + i + " " + tag, "Recife " + tag, false);
            insertTicketType(available, "Lote", "50.00", 10, 0, true);
        }

        search("q", tag, "soldOut", "false", "size", "2", "page", "0")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].soldOut").value(false))
                .andExpect(jsonPath("$.content[1].soldOut").value(false))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        search("q", tag, "soldOut", "false", "size", "2", "page", "1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].soldOut").value(false));
    }

    @Test
    void soldOutFilterCombinesWithNameAndCitySearch() throws Exception {
        var rockRecife = insertEvent("Rock " + tag, "Recife " + tag, false);
        insertTicketType(rockRecife, "Lote", "50.00", 10, 0, true);
        var rockRecifeSoldOut = insertEvent("Rock lotado " + tag, "Recife " + tag, false);
        insertTicketType(rockRecifeSoldOut, "Lote", "50.00", 10, 10, true);
        var rockNatal = insertEvent("Rock " + tag, "Natal " + tag, false);
        insertTicketType(rockNatal, "Lote", "50.00", 10, 0, true);
        var jazzRecife = insertEvent("Jazz " + tag, "Recife " + tag, false);
        insertTicketType(jazzRecife, "Lote", "50.00", 10, 0, true);

        search("q", "rock " + tag, "city", "recife " + tag, "soldOut", "false")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(rockRecife.toString())));
    }

    @Test
    void featuredEventsHideSoldOutWithFilter() throws Exception {
        var featuredAvailable = insertEvent("Destaque " + tag, "Recife " + tag, true);
        insertTicketType(featuredAvailable, "Lote", "50.00", 10, 0, true);
        var featuredSoldOut = insertEvent("Destaque lotado " + tag, "Recife " + tag, true);
        insertTicketType(featuredSoldOut, "Lote", "50.00", 10, 10, true);
        var notFeatured = insertEvent("Comum " + tag, "Recife " + tag, false);
        insertTicketType(notFeatured, "Lote", "50.00", 10, 0, true);

        search("q", tag, "featured", "true", "soldOut", "false")
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(featuredAvailable.toString())))
                .andExpect(jsonPath("$.content[0].featured").value(true));
    }

    @Test
    void organizerListIncludesPricingAndSoldOutEvents() throws Exception {
        var available = insertEvent("Disponivel " + tag, "Recife " + tag, false);
        insertTicketType(available, "Lote", "65.50", 10, 0, true);
        var soldOut = insertEvent("Esgotado " + tag, "Recife " + tag, false);
        insertTicketType(soldOut, "Lote", "50.00", 10, 10, true);
        var token = tokenService.generateToken(userRepository.findById(organizer).orElseThrow());

        mockMvc.perform(get("/events/mine").param("sort", "name").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(available.toString()))
                .andExpect(jsonPath("$.content[0].minPrice").value(65.50))
                .andExpect(jsonPath("$.content[0].soldOut").value(false))
                .andExpect(jsonPath("$.content[1].id").value(soldOut.toString()))
                .andExpect(jsonPath("$.content[1].minPrice").isEmpty())
                .andExpect(jsonPath("$.content[1].soldOut").value(true));
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/events");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private UUID insertEvent(String name, String city, boolean featured) {
        var id = UUID.randomUUID();
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        jdbc.update("""
                INSERT INTO events (id, name, venue_name, address, city, state, starts_at, organizer_id, active, featured)
                VALUES (?, ?, 'Arena', 'Rua A, 100', ?, 'PE', ?, ?, true, ?)
                """, id, name, city, Timestamp.from(startsAt), organizer, featured);
        return id;
    }

    private void insertTicketType(UUID eventId, String name, String price, int total, int sold, boolean active) {
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold, max_per_order, active)
                VALUES (?, ?, ?, ?, ?, ?, 4, ?)
                """, UUID.randomUUID(), eventId, name, new BigDecimal(price), total, sold, active);
    }
}
