package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.payout.PayoutTestBase;
import com.lutfy.ticketfy.privacy.AccountDeletionRequestDTO;
import com.lutfy.ticketfy.privacy.AccountDeletionService;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParticipantIntegrationTest extends PayoutTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";

    @Autowired
    private TicketTransferService transfers;

    @Autowired
    private AccountDeletionService deletions;

    private Instant startsAt;
    private UUID eventId;
    private UUID pista;
    private UUID vip;
    private User ana;
    private User bruno;
    private User carla;
    private User dani;

    @BeforeEach
    void createParticipants() throws Exception {
        startsAt = clock.instant().plus(Duration.ofDays(10));
        eventId = insertEvent(organizer.getId(), startsAt, startsAt.plus(Duration.ofHours(3)));
        pista = insertTicketType(eventId, "50.00");
        vip = insertTicketType(eventId, "150.00");
        ana = named("Ana Lima");
        bruno = named("Bruno Souza");
        carla = named("=Carla Dias");
        dani = named("Dani_Rocha");

        paidOrder(ana, pista, 2);
        paidOrder(bruno, vip, 1);
        var refunded = paidOrder(dani, pista, 1);
        orderService.refund(refunded, dani);

        var anaTicket = jdbc.queryForObject("SELECT id FROM tickets WHERE owner_id = ? ORDER BY id LIMIT 1",
                UUID.class, ana.getId());
        transfers.transfer(anaTicket, new TicketTransferRequestDTO(carla.getEmail(), PASSWORD), ana);

        var brunoCode = jdbc.queryForObject("SELECT code FROM tickets WHERE owner_id = ?", String.class, bruno.getId());
        mockMvc.perform(post("/tickets/{code}/check-in", brunoCode).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
    }

    @Test
    void listsEveryTicketWithTheCurrentHolderAndMaskedCode() throws Exception {
        var body = list(organizer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].holderName").value(contains(
                        "=Carla Dias", "Ana Lima", "Bruno Souza", "Dani_Rocha")))
                .andExpect(jsonPath("$.content[0].transferred").value(true))
                .andExpect(jsonPath("$.content[1].transferred").value(false))
                .andExpect(jsonPath("$.content[2].status").value("USED"))
                .andExpect(jsonPath("$.content[2].checkedInAt").isNotEmpty())
                .andExpect(jsonPath("$.content[3].status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (var code : jdbc.queryForList("""
                SELECT t.code FROM tickets t JOIN ticket_types tt ON tt.id = t.ticket_type_id WHERE tt.event_id = ?
                """, String.class, eventId)) {
            assertThat(body).doesNotContain(code).contains("\"codeSuffix\":\"" + code.substring(12) + "\"");
        }
        assertThat(body).doesNotContain("@test.com").doesNotContain("\"email\"");
    }

    @Test
    void filtersByLotStatusAndName() throws Exception {
        list(organizer, "ticketTypeId", vip.toString())
                .andExpect(jsonPath("$.content[*].holderName").value(contains("Bruno Souza")));
        list(organizer, "status", "USED")
                .andExpect(jsonPath("$.content[*].holderName").value(contains("Bruno Souza")));
        list(organizer, "status", "VALID")
                .andExpect(jsonPath("$.content[*].holderName").value(containsInAnyOrder("=Carla Dias", "Ana Lima")));
        list(organizer, "q", "  aNa ")
                .andExpect(jsonPath("$.content[*].holderName").value(contains("Ana Lima")));
        list(organizer, "q", "_")
                .andExpect(jsonPath("$.content[*].holderName").value(contains("Dani_Rocha")));
        list(organizer, "q", "%")
                .andExpect(jsonPath("$.content.length()").value(0));
        list(organizer, "status", "INVALIDO")
                .andExpect(status().isBadRequest());
        var foreignEvent = insertEvent(user("ORGANIZER").getId(), startsAt, startsAt.plus(Duration.ofHours(1)));
        list(organizer, "ticketTypeId", insertTicketType(foreignEvent, "10.00").toString())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void paginates() throws Exception {
        list(organizer, "size", "3", "page", "1")
                .andExpect(jsonPath("$.content[*].holderName").value(contains("Dani_Rocha")))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        list(organizer, "size", "2", "page", "0")
                .andExpect(jsonPath("$.content[*].holderName").value(contains("=Carla Dias", "Ana Lima")));
    }

    @Test
    void onlyTheOrganizerOrAnAdminSeeTheList() throws Exception {
        for (var outsider : List.of(ana, user("USER"), user("ORGANIZER"))) {
            list(outsider)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "event-not-found"));
            mockMvc.perform(get("/events/{id}/participants.csv", eventId).header("Authorization", bearer(outsider)))
                    .andExpect(status().isNotFound());
        }
        list(user("ADMIN")).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(4));
        mockMvc.perform(get("/events/{id}/participants", eventId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/events/{id}/participants", UUID.randomUUID()).header("Authorization", bearer(organizer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportsCsvWithFormulaProtectionAndAudit() throws Exception {
        var csv = mockMvc.perform(get("/events/{id}/participants.csv", eventId)
                        .param("status", "VALID")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var lines = csv.substring(1).split("\r\n");
        assertThat(lines[0]).isEqualTo("Participante;Lote;Situação;Check-in;Transferido;Final do código");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).startsWith("'=Carla Dias;").contains(";Válido;;Sim;");
        assertThat(lines[2]).startsWith("Ana Lima;").contains(";Não;");
        assertThat(jdbc.queryForObject("""
                SELECT details->>'rows' FROM audit_log WHERE action = 'PARTICIPANTS_EXPORTED' AND target_id = ?
                """, String.class, eventId)).isEqualTo("2");
    }

    @Test
    void deletedAccountsAppearAnonymized() throws Exception {
        var pastStart = clock.instant().plus(Duration.ofDays(1));
        var pastEvent = insertEvent(organizer.getId(), pastStart, pastStart.plus(Duration.ofHours(2)));
        var lot = insertTicketType(pastEvent, "20.00");
        var leaving = named("Elisa Prado");
        paidOrder(leaving, lot, 1);
        travelTo(pastStart.plus(Duration.ofDays(1)));

        deletions.delete(leaving, new AccountDeletionRequestDTO(PASSWORD, "EXCLUIR"));

        mockMvc.perform(get("/events/{id}/participants", pastEvent).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].holderName").value(everyItem(is(User.DELETED_NAME))));
    }

    private User named(String name) {
        var created = user("USER");
        jdbc.update("UPDATE users SET name = ? WHERE id = ?", name, created.getId());
        return userRepository.findById(created.getId()).orElseThrow();
    }

    private UUID paidOrder(User who, UUID lot, int quantity) {
        var orderId = orderService.create(new OrderCreationDTO(List.of(new OrderItemRequestDTO(lot, quantity)), null),
                null, who).id();
        paymentService.paySimulated(orderId, who);
        return orderId;
    }

    private ResultActions list(User who, String... params) throws Exception {
        var request = get("/events/{id}/participants", eventId).header("Authorization", bearer(who));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }
}
