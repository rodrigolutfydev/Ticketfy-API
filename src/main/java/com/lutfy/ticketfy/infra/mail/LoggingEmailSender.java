package com.lutfy.ticketfy.infra.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Usado quando não há chave da Brevo (desenvolvimento). Só registra que um
// e-mail sairia, sem destinatário nem conteúdo.
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Email not sent (Brevo is not configured): {} attachment(s)", message.attachments().size());
    }
}
