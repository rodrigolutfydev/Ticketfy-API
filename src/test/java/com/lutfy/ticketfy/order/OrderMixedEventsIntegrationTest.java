package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderMixedEventsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void rejectsItemsFromDifferentEventsWithoutReservingStock() throws Exception {
        var organizer = insertUser("ORGANIZER");
        var firstTicketType = insertTicketType(insertEvent(organizer), 10);
        var secondTicketType = insertTicketType(insertEvent(organizer), 10);
        var buyer = userRepository.findById(insertUser("USER")).orElseThrow();

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + tokenService.generateToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(items(firstTicketType, secondTicketType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("All items of an order must belong to the same event"));

        assertThat(sold(firstTicketType)).isZero();
        assertThat(sold(secondTicketType)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Long.class, buyer.getId()))
                .isZero();
    }

    @Test
    void acceptsItemsFromDifferentTicketTypesOfTheSameEvent() throws Exception {
        var organizer = insertUser("ORGANIZER");
        var event = insertEvent(organizer);
        var firstTicketType = insertTicketType(event, 10);
        jdbc.update("UPDATE ticket_types SET name = 'Pista' WHERE id = ?", firstTicketType);
        var secondTicketType = insertTicketType(event, 10);
        var buyer = userRepository.findById(insertUser("USER")).orElseThrow();

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + tokenService.generateToken(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(items(firstTicketType, secondTicketType)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    private static String items(UUID first, UUID second) {
        return "{\"items\":[{\"ticketTypeId\":\"" + first + "\",\"quantity\":1},"
                + "{\"ticketTypeId\":\"" + second + "\",\"quantity\":1}]}";
    }

    private Integer sold(UUID ticketTypeId) {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
    }
}
