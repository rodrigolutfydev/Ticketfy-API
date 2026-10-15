package com.lutfy.ticketfy.infra.mail;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Base64;
import java.util.List;

// Envio pela API transacional da Brevo (POST /v3/smtp/email). Os anexos vão
// em base64 no próprio JSON.
public class BrevoEmailSender implements EmailSender {

    private final MailSettings settings;
    private final RestClient restClient;

    public BrevoEmailSender(MailSettings settings) {
        this.settings = settings;
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void send(EmailMessage message) {
        var attachments = message.attachments().stream()
                .map(attachment -> new Attachment(attachment.fileName(),
                        Base64.getEncoder().encodeToString(attachment.content())))
                .toList();
        var body = new Request(
                new Contact(settings.fromEmail(), settings.fromName()),
                List.of(new Contact(message.toEmail(), message.toName())),
                message.subject(),
                message.html(),
                message.text(),
                attachments.isEmpty() ? null : attachments);
        try {
            restClient.post()
                    .uri(settings.brevoApiUri())
                    .header("api-key", settings.brevoApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new EmailDeliveryException("Brevo responded with status " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new EmailDeliveryException("Brevo unreachable: " + ex.getClass().getSimpleName());
        }
    }

    record Contact(String email, String name) {
    }

    record Attachment(String name, String content) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(Contact sender, List<Contact> to, String subject, String htmlContent, String textContent,
                   List<Attachment> attachment) {
    }
}
