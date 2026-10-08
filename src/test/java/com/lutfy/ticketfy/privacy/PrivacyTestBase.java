package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.user.User;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class PrivacyTestBase extends PayoutTestBase {

    protected User person(String role, String firstName) {
        var user = user(role);
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("UPDATE users SET name = ?, email = ?, avatar_url = ? WHERE id = ?",
                firstName + " Pessoa " + suffix, firstName.toLowerCase() + "." + suffix + "@example.com",
                "https://cdn.example.com/" + suffix + ".png", user.getId());
        return userRepository.findById(user.getId()).orElseThrow();
    }

    protected ResultActions exportData(User user, String password) throws Exception {
        return exportData(bearer(user), password);
    }

    protected ResultActions exportData(String authorization, String password) throws Exception {
        return mockMvc.perform(post("/users/me/data-export")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("password", password))));
    }

    protected ResultActions deleteAccount(User user, String password, String confirmation) throws Exception {
        return deleteAccount(bearer(user), password, confirmation);
    }

    protected ResultActions deleteAccount(String authorization, String password, String confirmation)
            throws Exception {
        return mockMvc.perform(post("/users/me/deletion")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("password", password, "confirmation", confirmation))));
    }

    protected UUID upcomingEvent(User owner) {
        var startsAt = Instant.now().plus(Duration.ofDays(30));
        return insertEvent(owner.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
    }

    protected UUID pastEvent(User owner) {
        var startsAt = Instant.now().minus(Duration.ofDays(30));
        return insertEvent(owner.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
    }

    protected UUID pendingOrder(User buyer, UUID ticketTypeId, int quantity) {
        return orderService.create(new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity))),
                null, buyer).id();
    }

    protected UUID paidOrder(User buyer, UUID ticketTypeId, int quantity) {
        var orderId = pendingOrder(buyer, ticketTypeId, quantity);
        paymentService.paySimulated(orderId, buyer);
        return orderId;
    }

    protected List<String> columnsContaining(String... values) {
        var columns = jdbc.queryForList("""
                SELECT table_name, column_name FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND data_type IN ('character varying', 'text', 'character', 'jsonb')
                   AND table_name <> 'flyway_schema_history'
                """);
        var found = new ArrayList<String>();
        for (var column : columns) {
            var table = (String) column.get("table_name");
            var name = (String) column.get("column_name");
            for (var value : values) {
                var count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE CAST(" + name + " AS text) ILIKE ?", Integer.class, "%" + value + "%");
                if (count != null && count > 0) {
                    found.add(table + "." + name);
                }
            }
        }
        return found;
    }
}
