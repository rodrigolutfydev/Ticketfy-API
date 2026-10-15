package com.lutfy.ticketfy.notification;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.calendar.IcsWriter;
import com.lutfy.ticketfy.infra.exception.InsufficientStockException;
import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.infra.mail.EmailAttachment;
import com.lutfy.ticketfy.infra.mail.EmailMessage;
import com.lutfy.ticketfy.infra.mail.MailConfig;
import com.lutfy.ticketfy.infra.mail.RecordingEmailSender;
import com.lutfy.ticketfy.infra.qr.QrCodePng;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.ticket.TicketTransferRequestDTO;
import com.lutfy.ticketfy.ticket.TicketTransferService;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@TestPropertySource(properties = "ticketfy.site.base-url=https://ticketfy.example")
class TransactionalEmailIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private RecordingEmailSender mail;

    @Autowired
    @Qualifier(MailConfig.EXECUTOR)
    private ThreadPoolTaskExecutor mailExecutor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TicketTransferService transferService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private QrCodePng qrCodes;

    private UUID eventId;
    private UUID paidLot;
    private UUID freeLot;

    @BeforeEach
    void setUp() {
        eventId = insertEvent(insertUser("ORGANIZER"));
        jdbc.update("UPDATE events SET name = 'Festival de Inverno', ends_at = NULL WHERE id = ?", eventId);
        paidLot = insertTicketType(eventId, 10);
        freeLot = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold, max_per_order, active)
                VALUES (?, ?, 'Cortesia', 0, 10, 0, 4, true)
                """, freeLot, eventId);
    }

    @AfterEach
    void tearDown() {
        mail.failing(false);
        reset(qrCodes);
    }

    @Test
    void paidPurchaseSendsConfirmationWithTicketsTotalQrCodesAndCalendar() throws Exception {
        var buyer = user("Ana Souza", "pt-BR");
        var orderId = orderService.create(order(paidLot, 2), null, buyer).id();
        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(buyer.getEmail())).as("pending order sends nothing").isEmpty();

        paymentService.paySimulated(orderId, buyer);

        var message = single(buyer);
        var codes = codes(orderId);
        assertThat(message.toName()).isEqualTo("Ana Souza");
        assertThat(message.subject()).isEqualTo("Compra confirmada: Festival de Inverno");
        assertThat(message.text())
                .contains("Olá, Ana Souza!", "Festival de Inverno", "Rua A, 100", "Horário de Brasília",
                        "Total pago", "160,00", "VIP", codes.get(0), codes.get(1),
                        "Ver meus ingressos: https://ticketfy.example/tickets");
        assertThat(message.html())
                .startsWith("<!doctype html>")
                .contains("lang=\"pt-BR\"", "href=\"https://ticketfy.example/tickets\"", "Ver meus ingressos",
                        codes.get(0), codes.get(1));

        assertThat(message.attachments()).extracting(EmailAttachment::fileName)
                .containsExactly("ticket-1.png", "ticket-2.png", "event.ics");
        assertThat(decodeQr(message.attachments().get(0))).isEqualTo(codes.get(0));
        assertThat(decodeQr(message.attachments().get(1))).isEqualTo(codes.get(1));
        var ics = message.attachments().get(2);
        assertThat(ics.contentType()).isEqualTo("text/calendar");
        var startsAt = startsAt();
        assertThat(new String(ics.content(), StandardCharsets.UTF_8))
                .contains("SUMMARY:Festival de Inverno",
                        "DTSTART:" + IcsWriter.formatUtc(startsAt),
                        "DTEND:" + IcsWriter.formatUtc(startsAt.plus(Duration.ofHours(3))));
    }

    @Test
    void freePurchaseIsConfirmedInTheBuyersLanguage() throws Exception {
        var buyer = user("John Smith", "en");
        var orderId = orderService.create(order(freeLot, 1), null, buyer).id();

        var message = single(buyer);
        assertThat(message.subject()).isEqualTo("Purchase confirmed: Festival de Inverno");
        assertThat(message.text())
                .contains("Hi, John Smith!", "Total paid", "Free", "Cortesia", codes(orderId).get(0),
                        "View my tickets: https://ticketfy.example/tickets")
                .doesNotContain("Ingressos", "Gratuito");
        assertThat(message.html()).contains("lang=\"en\"");
        assertThat(message.attachments()).extracting(EmailAttachment::fileName)
                .containsExactly("ticket-1.png", "event.ics");
        assertThat(decodeQr(message.attachments().get(0))).isEqualTo(codes(orderId).get(0));
    }

    @Test
    void failedOrderOrPaymentSendsNothing() {
        var buyer = user("Ana Souza", "pt-BR");

        jdbc.update("UPDATE ticket_types SET quantity_sold = 9 WHERE id = ?", paidLot);
        assertThatThrownBy(() -> orderService.create(order(paidLot, 2), null, buyer))
                .isInstanceOf(InsufficientStockException.class);
        var orderId = orderService.create(order(paidLot, 1), null, buyer).id();
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), orderId);
        assertThatThrownBy(() -> paymentService.paySimulated(orderId, buyer))
                .isInstanceOf(InvalidOrderStateException.class);

        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(buyer.getEmail())).isEmpty();
    }

    @Test
    void rolledBackConfirmationSendsNothing() {
        var freeBuyer = user("Ana Souza", "pt-BR");
        var freeOrder = transactionTemplate.execute(status -> {
            var id = orderService.create(order(freeLot, 1), null, freeBuyer).id();
            status.setRollbackOnly();
            return id;
        });

        var paidBuyer = user("Bruno Lima", "pt-BR");
        var paidOrder = orderService.create(order(paidLot, 1), null, paidBuyer).id();
        transactionTemplate.executeWithoutResult(status -> {
            paymentService.paySimulated(paidOrder, paidBuyer);
            status.setRollbackOnly();
        });

        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id = ?", Long.class, freeOrder)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, paidOrder)).isEqualTo("PENDING");
        assertThat(mail.sentTo(freeBuyer.getEmail())).isEmpty();
        assertThat(mail.sentTo(paidBuyer.getEmail())).isEmpty();
    }

    @Test
    void deliveryFailureNeverUndoesThePurchase() {
        var buyer = user("Ana Souza", "pt-BR");
        mail.failing(true);

        var orderId = orderService.create(order(freeLot, 1), null, buyer).id();

        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(buyer.getEmail())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PAID");
        assertThat(codes(orderId)).hasSize(1);
    }

    @Test
    void qrFailureStillSendsTheEmailWithoutThatAttachment() {
        doThrow(new IllegalStateException("boom")).when(qrCodes).render(anyString());
        var buyer = user("Ana Souza", "pt-BR");

        orderService.create(order(freeLot, 2), null, buyer);

        var message = single(buyer);
        assertThat(message.attachments()).extracting(EmailAttachment::fileName).containsExactly("event.ics");
    }

    @Test
    void transferEmailsTheRecipientWithTheNewQrAndConfirmsToTheSender() throws Exception {
        var sender = user("Ana Souza", "pt-BR");
        var recipient = user("John Smith", "en");
        var orderId = orderService.create(order(freeLot, 1), null, sender).id();
        single(sender);
        var ticketId = jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ?", UUID.class, orderId);
        var oldCode = codes(orderId).get(0);

        transferService.transfer(ticketId, new TicketTransferRequestDTO(recipient.getEmail(), PASSWORD), sender);

        var newCode = jdbc.queryForObject("SELECT code FROM tickets WHERE id = ?", String.class, ticketId);
        var received = single(recipient);
        assertThat(received.subject()).isEqualTo("Ana Souza transferred a ticket to you");
        assertThat(received.text())
                .contains("Hi, John Smith!", "Ana Souza transferred a ticket to your account.",
                        "Festival de Inverno", "Cortesia", newCode,
                        "View my tickets: https://ticketfy.example/tickets")
                .doesNotContain(oldCode);
        assertThat(received.attachments()).extracting(EmailAttachment::fileName).containsExactly("ticket.png");
        assertThat(decodeQr(received.attachments().get(0))).isEqualTo(newCode);

        var confirmations = mail.await(sender.getEmail(), 2);
        var sent = confirmations.get(1);
        assertThat(sent.subject()).isEqualTo("Transferência concluída: Festival de Inverno");
        assertThat(sent.text())
                .contains("Você transferiu um ingresso para John Smith.", "Cortesia",
                        "O QR Code antigo deste ingresso deixou de valer")
                .doesNotContain(oldCode, newCode);
        assertThat(sent.attachments()).isEmpty();
    }

    @Test
    void htmlEscapesUserProvidedText() {
        jdbc.update("UPDATE events SET name = '<b>Show & Cia</b>' WHERE id = ?", eventId);
        var buyer = user("Ana <script>", "pt-BR");

        orderService.create(order(freeLot, 1), null, buyer);

        var message = single(buyer);
        assertThat(message.html())
                .contains("&lt;b&gt;Show &amp; Cia&lt;/b&gt;", "Ana &lt;script&gt;")
                .doesNotContain("<b>Show", "<script>");
        assertThat(message.text()).contains("<b>Show & Cia</b>");
    }

    private EmailMessage single(User user) {
        var messages = mail.await(user.getEmail(), 1);
        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(user.getEmail())).hasSize(messages.size());
        return messages.get(0);
    }

    private User user(String name, String language) {
        var id = insertUser("USER");
        jdbc.update("UPDATE users SET name = ?, language = ?, password = ? WHERE id = ?",
                name, language, passwordEncoder.encode(PASSWORD), id);
        return userRepository.findById(id).orElseThrow();
    }

    private static OrderCreationDTO order(UUID lot, int quantity) {
        return new OrderCreationDTO(List.of(new OrderItemRequestDTO(lot, quantity)));
    }

    private List<String> codes(UUID orderId) {
        return jdbc.queryForList("SELECT code FROM tickets WHERE order_id = ? ORDER BY created_at, id",
                String.class, orderId);
    }

    private Instant startsAt() {
        return jdbc.queryForObject("SELECT starts_at FROM events WHERE id = ?", Timestamp.class, eventId).toInstant();
    }

    private static String decodeQr(EmailAttachment attachment) throws Exception {
        assertThat(attachment.contentType()).isEqualTo("image/png");
        var image = ImageIO.read(new ByteArrayInputStream(attachment.content()));
        var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}
