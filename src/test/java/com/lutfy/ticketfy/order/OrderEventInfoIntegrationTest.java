package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderEventInfoIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void listAndDetailsBringTheEventNameAndDate() throws Exception {
        var organizer = insertUser("ORGANIZER");
        var buyer = userRepository.findById(insertUser("USER")).orElseThrow();
        var first = insertEvent(organizer);
        var second = insertEvent(organizer);
        jdbc.update("UPDATE events SET name = 'Festival de Inverno' WHERE id = ?", second);
        var firstOrder = order(buyer, insertTicketType(first, 10));
        var secondOrder = order(buyer, insertTicketType(second, 10));

        mockMvc.perform(get("/orders").param("sort", "createdAt,asc").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(firstOrder.toString()))
                .andExpect(jsonPath("$.content[0].eventId").value(first.toString()))
                .andExpect(jsonPath("$.content[0].eventName").value("Test Event"))
                .andExpect(jsonPath("$.content[0].eventStartsAt").value(startsAt(first)))
                .andExpect(jsonPath("$.content[1].id").value(secondOrder.toString()))
                .andExpect(jsonPath("$.content[1].eventId").value(second.toString()))
                .andExpect(jsonPath("$.content[1].eventName").value("Festival de Inverno"))
                .andExpect(jsonPath("$.content[1].eventStartsAt").value(startsAt(second)));

        mockMvc.perform(get("/orders/{id}", secondOrder).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(second.toString()))
                .andExpect(jsonPath("$.eventName").value("Festival de Inverno"))
                .andExpect(jsonPath("$.eventStartsAt").value(startsAt(second)));
    }

    @Test
    void emptyListStillWorks() throws Exception {
        var buyer = userRepository.findById(insertUser("USER")).orElseThrow();

        mockMvc.perform(get("/orders").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private UUID order(User buyer, UUID ticketTypeId) {
        return orderService.create(new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, 1))), null, buyer).id();
    }

    private String startsAt(UUID eventId) {
        return jdbc.queryForObject("SELECT starts_at FROM events WHERE id = ?", Timestamp.class, eventId)
                .toInstant().toString();
    }

    private String bearer(User user) {
        return "Bearer " + accessToken(user);
    }
}
