package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.security.TokenService;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
abstract class PayoutTestBase extends IntegrationTestBase {

    static final String PASSWORD = "Senha-forte-123";
    static final String CPF = "52998224725";
    static final String OTHER_CPF = "11144477735";
    static final String CNPJ = "11222333000181";
    static final String ALPHANUMERIC_CNPJ = "12ABC34501DE35";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TokenService tokenService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentService paymentService;

    @MockitoBean
    Clock clock;

    @MockitoSpyBean
    PayoutGateway payoutGateway;

    final AtomicReference<Instant> now = new AtomicReference<>();
    User organizer;
    User buyer;

    @BeforeEach
    void setUpPayoutBase() {
        now.set(null);
        when(clock.instant()).thenAnswer(invocation -> {
            var fixed = now.get();
            return fixed == null ? Instant.now() : fixed;
        });
        organizer = user("ORGANIZER");
        buyer = user("USER");
    }

    void travelTo(Instant instant) {
        now.set(instant);
    }

    void travel(Duration duration) {
        now.set(clock.instant().plus(duration));
    }

    Instant releasedSales(User owner, String... prices) {
        var startsAt = Instant.now().plus(Duration.ofDays(1));
        var endsAt = startsAt.plus(Duration.ofHours(4));
        var eventId = insertEvent(owner.getId(), startsAt, endsAt);
        for (var price : prices) {
            var ticketTypeId = insertTicketType(eventId, price);
            var orderId = orderService.create(
                    new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, 1))), null, buyer).id();
            paymentService.paySimulated(orderId, buyer);
        }
        var released = endsAt.plus(Duration.ofDays(2)).plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS);
        travelTo(released);
        return released;
    }

    ResultActions saveAccount(User user, String documentType, String document, String pixKeyType, String pixKey,
                              String password) throws Exception {
        var body = JSON.writeValueAsString(Map.of(
                "documentType", documentType,
                "document", document,
                "holderName", "Maria da Silva",
                "pixKeyType", pixKeyType,
                "pixKey", pixKey,
                "password", password));
        return mockMvc.perform(put("/organizer/payout-account")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    void registerAccount(User user) throws Exception {
        saveAccount(user, "CPF", CPF, "EMAIL", "maria.silva@example.com", PASSWORD).andExpect(status().isOk());
    }

    ResultActions requestPayout(User user, String amount, String password, String idempotencyKey) throws Exception {
        var request = post("/organizer/payouts")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"password\":\"" + password + "\"}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    JsonNode balance(User user) throws Exception {
        return JSON.readTree(mockMvc.perform(get("/organizer/balance").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    BigDecimal available(User user) throws Exception {
        return balance(user).get("available").decimalValue();
    }

    JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    User user(String role) {
        var id = insertUser(role);
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), id);
        return userRepository.findById(id).orElseThrow();
    }

    String bearer(User user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    UUID insertEvent(UUID organizerId, Instant startsAt, Instant endsAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, name, description, venue_name, address, city, state,
                                    starts_at, ends_at, organizer_id, active, created_at)
                VALUES (?, ?, 'Evento de teste', 'Arena', 'Rua A, 100', 'Rio de Janeiro', 'RJ', ?, ?, ?, true, NOW())
                """, id, "Evento " + id, Timestamp.from(startsAt), Timestamp.from(endsAt), organizerId);
        return id;
    }

    UUID insertTicketType(UUID eventId, String price) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, description, price,
                                          quantity_total, quantity_sold, max_per_order, active, created_at)
                VALUES (?, ?, ?, 'Lote de teste', ?, 100, 0, 10, true, NOW())
                """, id, eventId, "Lote " + id.toString().substring(0, 8), new BigDecimal(price));
        return id;
    }
}
