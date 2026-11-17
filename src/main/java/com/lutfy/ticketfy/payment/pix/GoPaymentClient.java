package com.lutfy.ticketfy.payment.pix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Component
public class GoPaymentClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String internalToken;

    public GoPaymentClient(
            ObjectMapper mapper,
            @Value("${ticketfy.payments.go-url}") String baseUrl,
            @Value("${ticketfy.payments.internal-token}") String internalToken
    ) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalToken = internalToken;

        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "Token interno do serviço Go não configurado"
            );
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public GoPayment createPayment(
            UUID orderId,
            long amountCents
    ) {
        try {
            var payload = new CreatePaymentRequest(
                    orderId.toString(),
                    amountCents
            );

            String json = mapper.writeValueAsString(payload);

            HttpRequest request = requestBuilder(
                    "/internal/payments"
            )
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = send(request);

            if (response.statusCode() != 200
                    && response.statusCode() != 201) {

                throw new GoPaymentException(
                        "Falha ao criar pagamento no Go. HTTP "
                                + response.statusCode()
                );
            }

            return mapper.readValue(
                    response.body(),
                    GoPayment.class
            );

        } catch (IOException e) {
            throw new GoPaymentException(
                    "Resposta inválida do serviço de pagamentos",
                    e
            );
        }
    }

    public GoPix createPix(UUID paymentId) {
        try {
            HttpRequest request = requestBuilder(
                    "/internal/payments/" + paymentId + "/pix"
            )
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = send(request);

            if (response.statusCode() != 201) {
                throw new GoPaymentException(
                        "Falha ao gerar Pix. HTTP "
                                + response.statusCode()
                );
            }

            return mapper.readValue(
                    response.body(),
                    GoPix.class
            );

        } catch (IOException e) {
            throw new GoPaymentException(
                    "Resposta Pix inválida",
                    e
            );
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + internalToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new GoPaymentException(
                    "Comunicação interrompida",
                    e
            );

        } catch (IOException e) {
            throw new GoPaymentException(
                    "Serviço de pagamentos indisponível",
                    e
            );
        }
    }

    public record CreatePaymentRequest(
            String orderId,
            long amountCents
    ) {}

   @JsonIgnoreProperties(ignoreUnknown = true)
public record GoPayment(
    UUID id,
    String orderId,
    long amountCents,
    String currency,
    String status
) {}

    public record GoPix(
            String paymentId,
            String pixCopyPaste,
            String pixQrCode,
            String status
    ) {}

    public static class GoPaymentException
            extends RuntimeException {

        public GoPaymentException(String message) {
            super(message);
        }

        public GoPaymentException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}