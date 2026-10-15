package com.lutfy.ticketfy.infra.mail;

/**
 * Envia um e-mail já montado. Falhas viram {@link EmailDeliveryException};
 * quem chama decide o que fazer (os e-mails transacionais só registram no log).
 */
public interface EmailSender {

    void send(EmailMessage message);
}
