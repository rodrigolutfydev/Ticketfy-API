package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TicketTypePublicListIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;


    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = insertEvent(insertUser("ORGANIZER"));
    }

    @Test
    void hidesRemainingWhenStockIsNotLow() throws Exception {
        var ticketTypeId = insertTicketType(eventId, 100);
        setSold(ticketTypeId, 89);

        mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$[0].description").value("Test ticket type"))
                .andExpect(jsonPath("$[0].maxPerOrder").value(4))
                .andExpect(jsonPath("$[0].price").value(80.00))
                .andExpect(jsonPath("$[0].soldOut").value(false))
                .andExpect(jsonPath("$[0].remaining").isEmpty());
    }

    @Test
    void showsRemainingWhenTenOrLessAreLeft() throws Exception {
        var ticketTypeId = insertTicketType(eventId, 100);
        setSold(ticketTypeId, 90);

        mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].soldOut").value(false))
                .andExpect(jsonPath("$[0].remaining").value(10));
    }

    @Test
    void marksSoldOutWithoutRemaining() throws Exception {
        var ticketTypeId = insertTicketType(eventId, 50);
        setSold(ticketTypeId, 50);

        mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].soldOut").value(true))
                .andExpect(jsonPath("$[0].remaining").isEmpty());
    }

    @Test
    void doesNotExposeSalesVolume() throws Exception {
        insertTicketType(eventId, 100);

        var body = mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("quantityTotal", "quantitySold", "available");
    }

    @Test
    void returnsNotFoundForUnknownEvent() throws Exception {
        mockMvc.perform(get("/events/{id}/ticket-types", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderAboveMaxPerOrderIsRejectedWithBadRequest() throws Exception {
        var ticketTypeId = insertTicketType(eventId, 100);
        var buyer = userRepository.findById(insertUser("USER")).orElseThrow();

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":5}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Maximum 4 tickets per order for this ticket type"));

        var sold = jdbc.queryForObject(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(sold).isZero();
    }

    private void setSold(UUID ticketTypeId, int quantitySold) {
        jdbc.update("UPDATE ticket_types SET quantity_sold = ? WHERE id = ?", quantitySold, ticketTypeId);
    }
}
