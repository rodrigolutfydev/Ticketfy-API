package com.lutfy.ticketfy.infra.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrevoEmailSenderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String responseBody = "{\"messageId\":\"<1@brevo>\"}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheMessageWithBase64AttachmentsToBrevo() {
        var png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};

        sender("key-123").send(new EmailMessage("ana@example.com", "Ana", "Assunto", "<p>Oi</p>", "Oi",
                List.of(new EmailAttachment("ticket-1.png", "image/png", png))));

        assertThat(apiKey.get()).isEqualTo("key-123");
        JsonNode json = JSON.readTree(body.get());
        assertThat(json.at("/sender/email").asString()).isEqualTo("noreply@ticketfy.example");
        assertThat(json.at("/sender/name").asString()).isEqualTo("Ticketfy");
        assertThat(json.at("/to/0/email").asString()).isEqualTo("ana@example.com");
        assertThat(json.at("/to/0/name").asString()).isEqualTo("Ana");
        assertThat(json.at("/subject").asString()).isEqualTo("Assunto");
        assertThat(json.at("/htmlContent").asString()).isEqualTo("<p>Oi</p>");
        assertThat(json.at("/textContent").asString()).isEqualTo("Oi");
        assertThat(json.at("/attachment/0/name").asString()).isEqualTo("ticket-1.png");
        assertThat(Base64.getDecoder().decode(json.at("/attachment/0/content").asString())).isEqualTo(png);
    }

    @Test
    void messageWithoutAttachmentsOmitsTheField() {
        sender("key-123").send(new EmailMessage("ana@example.com", "Ana", "Assunto", "<p>Oi</p>", "Oi", List.of()));

        assertThat(JSON.readTree(body.get()).has("attachment")).isFalse();
    }

    @Test
    void errorResponseBecomesDeliveryExceptionWithoutTheResponseBody() {
        status = 400;
        responseBody = "{\"code\":\"invalid_parameter\",\"message\":\"secret detail\"}";

        assertThatThrownBy(() -> sender("key-123").send(
                new EmailMessage("ana@example.com", "Ana", "Assunto", "<p>Oi</p>", "Oi", List.of())))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Brevo responded with status 400");
    }

    @Test
    void apiKeyWithoutSenderAddressIsRejectedAtStartup() {
        assertThatThrownBy(() -> new MailSettings("key-123", "https://api.brevo.com/v3/smtp/email", " ",
                "Ticketfy", 10, "America/Sao_Paulo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ticketfy.mail.from-email");
    }

    private BrevoEmailSender sender(String key) {
        var uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
        return new BrevoEmailSender(new MailSettings(key, uri, "noreply@ticketfy.example", "Ticketfy", 5, "America/Sao_Paulo"));
    }
}
