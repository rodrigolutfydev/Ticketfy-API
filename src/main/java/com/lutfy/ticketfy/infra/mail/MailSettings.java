package com.lutfy.ticketfy.infra.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

@Component
public class MailSettings {

    private final String brevoApiKey;
    private final URI brevoApiUri;
    private final String fromEmail;
    private final String fromName;
    private final Duration timeout;
    private final ZoneId timeZone;

    public MailSettings(@Value("${ticketfy.mail.brevo.api-key:}") String brevoApiKey,
                        @Value("${ticketfy.mail.brevo.api-uri:https://api.brevo.com/v3/smtp/email}") String brevoApiUri,
                        @Value("${ticketfy.mail.from-email:}") String fromEmail,
                        @Value("${ticketfy.mail.from-name:Ticketfy}") String fromName,
                        @Value("${ticketfy.mail.timeout-seconds:5}") long timeoutSeconds,
                        @Value("${ticketfy.mail.time-zone:America/Sao_Paulo}") String timeZone) {
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.brevoApiUri = URI.create(brevoApiUri);
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null || fromName.isBlank() ? "Ticketfy" : fromName.trim();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.timeZone = ZoneId.of(timeZone);
    }

    /** Brevo só entra com chave e remetente; faltando um dos dois, os e-mails vão só para o log. */
    public boolean brevoEnabled() {
        return !brevoApiKey.isEmpty() && !fromEmail.isEmpty();
    }

    public String brevoApiKey() {
        return brevoApiKey;
    }

    public URI brevoApiUri() {
        return brevoApiUri;
    }

    public String fromEmail() {
        return fromEmail;
    }

    public String fromName() {
        return fromName;
    }

    public Duration timeout() {
        return timeout;
    }

    /** Fuso usado para mostrar data e hora dos eventos nos e-mails. */
    public ZoneId timeZone() {
        return timeZone;
    }
}
