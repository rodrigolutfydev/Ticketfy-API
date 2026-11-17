package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.ClockedIntegrationTestBase;
import com.lutfy.ticketfy.payment.pix.GoPaymentClient;
import com.lutfy.ticketfy.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesWindowIntegrationTest extends ClockedIntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private GoPaymentClient goPaymentClient;

    private Instant startsAt;
    private UUID eventId;
    private UUID lotId;
    private User buyer;

    @BeforeEach
    void setUpEvent() {
        startsAt = futureStart();

        var organizer = insertUser("ORGANIZER");

        eventId = event(
                organizer,
                "Show",
                startsAt,
                startsAt.plus(Duration.ofHours(3)),
                false
        );

        lotId = lot(eventId, "50.00", 10);

        buyer = user("USER");
    }

    @Test
    void purchaseIsRefusedAtTheStartOfTheEvent() throws Exception {
        travelTo(startsAt);

        createOrder()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(endsWith("/sales-closed")));

        assertThat(quantitySold()).isZero();
        assertThat(ordersOfBuyer()).isZero();
    }

    @Test
    void purchaseIsRefusedAfterTheEventStarted() throws Exception {
        travelTo(startsAt.plus(Duration.ofHours(1)));

        createOrder()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(endsWith("/sales-closed")));

        assertThat(quantitySold()).isZero();
    }

    @Test
    void purchaseIsAcceptedOneMinuteBeforeTheStart() throws Exception {
        travelTo(startsAt.minus(Duration.ofMinutes(1)));

        createOrder()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").value(
                        startsAt.plus(Duration.ofMinutes(14)).toString()
                ));

        assertThat(quantitySold()).isEqualTo(1);
    }

    @Test
    void pendingOrderCreatedBeforeTheStartCanCreatePixAfterTheStart()
            throws Exception {

        travelTo(startsAt.minus(Duration.ofMinutes(1)));

        UUID orderId = createdOrderId();

        UUID paymentId = UUID.randomUUID();

        when(goPaymentClient.createPayment(orderId, 5000L))
                .thenReturn(new GoPaymentClient.GoPayment(
                        paymentId,
                        orderId.toString(),
                        5000L,
                        "BRL",
                        "PENDING"
                ));

        when(goPaymentClient.createPix(paymentId))
                .thenReturn(new GoPaymentClient.GoPix(
                        paymentId.toString(),
                        "PIX_TESTE_COPIA_E_COLA",
                        "QR_CODE_TESTE",
                        "PENDING"
                ));

        travelTo(startsAt.plus(Duration.ofMinutes(5)));

        createPix(orderId)
                .andExpect(status().isCreated());

        verify(goPaymentClient)
                .createPayment(orderId, 5000L);

        verify(goPaymentClient)
                .createPix(paymentId);

        // Criar um Pix não significa confirmar o pagamento.
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                orderId
        )).isEqualTo("PENDING");

        // Nenhum ingresso deve ser emitido antes da confirmação.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE order_id = ?",
                Long.class,
                orderId
        )).isZero();

        // A reserva continua ocupando uma unidade do estoque.
        assertThat(quantitySold()).isEqualTo(1);
    }

    @Test
    void paymentIsRefusedOnceTheReservationExpires() throws Exception {
        travelTo(startsAt.minus(Duration.ofMinutes(1)));

        UUID orderId = createdOrderId();

        travelTo(startsAt.plus(Duration.ofMinutes(15)));

        createPix(orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(endsWith("/invalid-order-state")));

        // Nenhuma comunicação com o Go deve acontecer
        // quando a reserva já está expirada.
        verify(goPaymentClient, never())
                .createPayment(any(UUID.class), anyLong());

        verify(goPaymentClient, never())
                .createPix(any(UUID.class));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE order_id = ?",
                Long.class,
                orderId
        )).isZero();
    }

    @Test
    void expirationUsesTheClockAndReleasesTheStock() throws Exception {
        travelTo(startsAt.minus(Duration.ofHours(1)));

        UUID orderId = createdOrderId();

        assertThat(orderService.findOverdueOrderIds())
                .doesNotContain(orderId);

        travelTo(startsAt.minus(Duration.ofMinutes(44)));

        assertThat(orderService.findOverdueOrderIds())
                .contains(orderId);

        assertThat(orderService.expireOrder(orderId))
                .isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                orderId
        )).isEqualTo("EXPIRED");

        assertThat(quantitySold()).isZero();
    }

    @Test
    void couponPreviewIsRefusedOnceTheEventStarts() throws Exception {
        jdbc.update("""
                INSERT INTO coupons (
                    event_id,
                    code,
                    discount_type,
                    discount_value
                )
                VALUES (?, 'PROMO10', 'PERCENT', 10)
                """, eventId);

        travelTo(startsAt.minus(Duration.ofMinutes(1)));

        previewCoupon()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(45.00));

        travelTo(startsAt);

        previewCoupon()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(endsWith("/sales-closed")));
    }

    private ResultActions createOrder() throws Exception {
        return mockMvc.perform(
                post("/orders")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {
                                            "ticketTypeId": "%s",
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(lotId))
        );
    }

    private UUID createdOrderId() {
        return orderService.create(
                new OrderCreationDTO(
                        List.of(
                                new OrderItemRequestDTO(lotId, 1)
                        )
                ),
                null,
                buyer
        ).id();
    }

    private ResultActions createPix(UUID orderId) throws Exception {
        return mockMvc.perform(
                post("/orders/{id}/payment/pix", orderId)
                        .header("Authorization", bearer(buyer))
        );
    }

    private ResultActions previewCoupon() throws Exception {
        return mockMvc.perform(
                post("/events/{id}/coupons/preview", eventId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "promo10",
                                    "items": [
                                        {
                                            "ticketTypeId": "%s",
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(lotId))
        );
    }

    private int quantitySold() {
        return jdbc.queryForObject(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?",
                Integer.class,
                lotId
        );
    }

    private long ordersOfBuyer() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                Long.class,
                buyer.getId()
        );
    }
}