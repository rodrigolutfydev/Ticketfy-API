package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.infra.exception.TicketTypeNotFoundException;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketTypeDeactivationIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private TicketTypeService ticketTypes;

    private Instant startsAt;
    private UUID eventId;
    private UUID lot;
    private UUID otherLot;

    @BeforeEach
    void createEvent() {
        startsAt = clock.instant().plus(Duration.ofDays(20)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        lot = insertTicketType(eventId, "50.00");
        otherLot = insertTicketType(eventId, "80.00");
    }

    @Test
    void deactivatedLotStopsNewSalesButKeepsExistingOrdersAndTickets() throws Exception {
        var pending = order(buyer, lot, 2);
        var paid = order(buyer, lot, 1);
        paymentService.paySimulated(paid, buyer);

        deactivate(organizer, lot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.quantitySold").value(3));

        assertThatThrownBy(() -> order(buyer, lot, 1)).isInstanceOf(TicketTypeNotFoundException.class);
        mockMvc.perform(get("/events/{id}/ticket-types", eventId))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(lot.toString()))))
                .andExpect(jsonPath("$[*].id").value(hasItem(otherLot.toString())));
        mockMvc.perform(get("/events/{id}/ticket-types/manage", eventId).header("Authorization", bearer(organizer)))
                .andExpect(jsonPath("$[?(@.id == '" + lot + "')].active").value(hasItem(false)));
        mockMvc.perform(post("/events/{id}/coupons/preview", eventId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QUALQUER\",\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "ticket-type-not-found"));

        mockMvc.perform(post("/orders/{id}/payment", pending).header("Authorization", bearer(buyer)))
                .andExpect(status().isCreated());
        var code = jdbc.queryForObject("SELECT code FROM tickets WHERE order_id = ? LIMIT 1", String.class, paid);
        mockMvc.perform(post("/tickets/{code}/check-in", code).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());

        reactivate(organizer, lot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        order(buyer, lot, 1);
        assertThat(quantitySold(lot)).isEqualTo(4);
        assertThat(audits("TICKET_TYPE_DEACTIVATED", lot)).isEqualTo(1);
        assertThat(audits("TICKET_TYPE_REACTIVATED", lot)).isEqualTo(1);
    }

    @Test
    void inactiveLotCanStillBeEdited() throws Exception {
        deactivate(organizer, lot).andExpect(status().isOk());

        mockMvc.perform(patch("/events/{eventId}/ticket-types/{id}", eventId, lot)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":65.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(65.00))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void refusesInvalidDeactivationsAndReactivations() throws Exception {
        reactivate(organizer, lot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-ticket-type-state"))
                .andExpect(jsonPath("$.reason").value("already-active"));
        deactivate(organizer, lot).andExpect(status().isOk());
        deactivate(organizer, lot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-ticket-type-state"))
                .andExpect(jsonPath("$.reason").value("already-inactive"));

        var otherOrganizer = user("ORGANIZER");
        deactivate(otherOrganizer, otherLot)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "event-access-denied"));
        deactivate(user("USER"), otherLot).andExpect(status().isForbidden());
        deactivate(user("ADMIN"), otherLot).andExpect(status().isOk());

        var foreignEvent = insertEvent(otherOrganizer.getId(), startsAt, startsAt.plus(Duration.ofHours(2)));
        var foreignLot = insertTicketType(foreignEvent, "10.00");
        deactivate(organizer, eventId, foreignLot)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "ticket-type-not-found"));

        jdbc.update("UPDATE events SET cancelled_at = NOW() WHERE id = ?", eventId);
        reactivate(organizer, lot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-event-state"))
                .andExpect(jsonPath("$.reason").value("cancelled"));

        jdbc.update("UPDATE events SET active = false WHERE id = ?", foreignEvent);
        deactivate(otherOrganizer, foreignEvent, foreignLot)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "event-not-found"));
    }

    @Test
    void afterTheStartOnlyDeactivationIsAllowed() throws Exception {
        travelTo(startsAt.plus(Duration.ofMinutes(10)));

        deactivate(organizer, lot).andExpect(status().isOk());
        reactivate(organizer, lot)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "sales-closed"));
    }

    @Test
    void theLastActiveLotCanBeDeactivated() throws Exception {
        deactivate(organizer, lot).andExpect(status().isOk());
        deactivate(organizer, otherLot).andExpect(status().isOk());

        mockMvc.perform(get("/events/{id}/ticket-types", eventId)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/events/{id}", eventId)).andExpect(status().isOk());
    }

    @Test
    void deactivationRacingWithReservationsKeepsInvariants() throws Exception {
        var buyers = new ArrayList<User>();
        for (int i = 0; i < 8; i++) {
            buyers.add(user("USER"));
        }
        var actions = new ArrayList<Callable<Object>>();
        for (var who : buyers) {
            actions.add(() -> order(who, lot, 1));
        }
        actions.add(() -> ticketTypes.deactivate(eventId, lot, organizer));

        var outcomes = race(actions);

        assertThat(outcomes.get(outcomes.size() - 1)).isInstanceOf(TicketTypeDetailsDTO.class);
        for (var outcome : outcomes.subList(0, outcomes.size() - 1)) {
            if (!(outcome instanceof UUID)) {
                assertThat(outcome).isInstanceOf(TicketTypeNotFoundException.class);
            }
        }
        var created = outcomes.stream().filter(UUID.class::isInstance).map(UUID.class::cast).toList();
        assertThat(quantitySold(lot)).isEqualTo(created.size());
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                 WHERE oi.ticket_type_id = ? AND o.status IN ('PENDING', 'PAID')
                """, Integer.class, lot)).isEqualTo(created.size());
        assertThatThrownBy(() -> order(buyers.get(0), lot, 1)).isInstanceOf(TicketTypeNotFoundException.class);
        for (int i = 0; i < created.size(); i++) {
            var owner = jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", UUID.class, created.get(i));
            var payer = buyers.stream().filter(candidate -> candidate.getId().equals(owner)).findFirst().orElseThrow();
            paymentService.paySimulated(created.get(i), payer);
        }
    }

    private List<Object> race(List<Callable<Object>> actions) throws Exception {
        var ready = new CountDownLatch(actions.size());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(actions.size());
        try {
            var futures = new ArrayList<Future<Object>>();
            for (var action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return action.call();
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            var outcomes = new ArrayList<Object>();
            for (var future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID order(User who, UUID ticketTypeId, int quantity) {
        return orderService.create(new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity)), null),
                null, who).id();
    }

    private ResultActions deactivate(User who, UUID ticketTypeId) throws Exception {
        return deactivate(who, eventId, ticketTypeId);
    }

    private ResultActions deactivate(User who, UUID event, UUID ticketTypeId) throws Exception {
        return mockMvc.perform(post("/events/{eventId}/ticket-types/{id}/deactivate", event, ticketTypeId)
                .header("Authorization", bearer(who)));
    }

    private ResultActions reactivate(User who, UUID ticketTypeId) throws Exception {
        return mockMvc.perform(post("/events/{eventId}/ticket-types/{id}/reactivate", eventId, ticketTypeId)
                .header("Authorization", bearer(who)));
    }

    private int quantitySold(UUID ticketTypeId) {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
    }

    private int audits(String action, UUID targetId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ? AND target_id = ?", Integer.class,
                action, targetId);
    }
}
