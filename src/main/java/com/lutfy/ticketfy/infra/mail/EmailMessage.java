package com.lutfy.ticketfy.infra.mail;

import java.util.List;

public record EmailMessage(
        String toEmail,
        String toName,
        String subject,
        String html,
        String text,
        List<EmailAttachment> attachments
) {
    public EmailMessage {
        attachments = List.copyOf(attachments);
    }
}
