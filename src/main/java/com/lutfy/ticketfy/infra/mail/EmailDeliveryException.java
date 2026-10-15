package com.lutfy.ticketfy.infra.mail;

/** A mensagem nunca traz o conteúdo do e-mail, só o motivo da falha. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }
}
