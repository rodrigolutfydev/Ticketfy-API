package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.ClockedIntegrationTestBase;
import com.lutfy.ticketfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIdempotencyKeyIntegrationTest extends ClockedIntegrationTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private User buyer;
    private UUID lot;

    @BeforeEach
    void setUp() {
        var organizer = user("ORGANIZER");
        buyer = user("USER");
        var startsAt = futureStart();
        lot = lot(event(organizer.getId(), "Show", startsAt, startsAt.plus(Duration.ofHours(3)), false), "50.00", 10);
    }

    @Test
    void keyLongerThanOneHundredCharactersIsRejected() throws Exception {
        createOrder("k".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("size"))
                .andExpect(jsonPath("$.errors[0].params.min").value(1))
                .andExpect(jsonPath("$.errors[0].params.max").value(100));

        assertThat(orderCount()).isZero();
    }

    @Test
    void keyWithExactlyOneHundredCharactersIsAccepted() throws Exception {
        createOrder("k".repeat(100)).andExpect(status().isCreated());
    }

    @Test
    void emptyKeyIsRejected() throws Exception {
        createOrder("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("size"));

        assertThat(orderCount()).isZero();
    }

    @Test
    void keyWithCharactersOutsideVisibleAsciiIsRejected() throws Exception {
        for (var key : new String[] {"chave com espaço", "pedido-ção", "tab\there"}) {
            createOrder(key)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                    .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                    .andExpect(jsonPath("$.errors[0].code").value("invalid"));
        }

        assertThat(orderCount()).isZero();
    }

    @Test
    void uuidKeyStillReturnsTheSameOrderOnRetry() throws Exception {
        var key = UUID.randomUUID().toString();

        var first = createOrder(key).andExpect(status().isCreated()).andReturn();
        var second = createOrder(key).andExpect(status().isCreated()).andReturn();

        assertThat(orderId(second.getResponse().getContentAsString()))
                .isEqualTo(orderId(first.getResponse().getContentAsString()));
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void requestWithoutKeyStillWorks() throws Exception {
        createOrder(null).andExpect(status().isCreated());
    }

    @Test
    void bodyValidationKeepsReportingTheBodyFields() throws Exception {
        mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    private ResultActions createOrder(String idempotencyKey) throws Exception {
        var request = post("/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"ticketTypeId\":\"" + lot + "\",\"quantity\":1}]}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, buyer.getId());
    }

    private static String orderId(String body) {
        return JSON.readTree(body).get("id").asString();
    }
}
