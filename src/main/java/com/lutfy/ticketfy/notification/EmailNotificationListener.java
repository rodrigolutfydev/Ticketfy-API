package com.lutfy.ticketfy.notification;

import com.lutfy.ticketfy.auth.passwordreset.PasswordResetRequestedEvent;
import com.lutfy.ticketfy.infra.mail.EmailDeliveryException;
import com.lutfy.ticketfy.infra.mail.EmailMessage;
import com.lutfy.ticketfy.infra.mail.EmailSender;
import com.lutfy.ticketfy.infra.mail.MailConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Só depois do commit: compra ou transferência desfeita não gera e-mail. O
// envio vai para o mailExecutor e qualquer falha fica só no log; nada aqui
// pode voltar como erro para quem comprou ou transferiu.
@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final TransactionalEmails emails;
    private final EmailSender sender;
    private final TaskExecutor executor;

    public EmailNotificationListener(TransactionalEmails emails, EmailSender sender,
                                     @Qualifier(MailConfig.EXECUTOR) TaskExecutor executor) {
        this.emails = emails;
        this.sender = sender;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        dispatch("Purchase confirmation", "order", event.orderId(),
                () -> emails.purchaseConfirmation(event.orderId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketTransferred(TicketTransferredEvent event) {
        dispatch("Transfer received", "ticket", event.ticketId(),
                () -> emails.transferReceived(event.ticketId(), event.senderId(), event.recipientId()));
        dispatch("Transfer sent", "ticket", event.ticketId(),
                () -> emails.transferSent(event.ticketId(), event.senderId(), event.recipientId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        dispatch("Password reset", "user", event.userId(),
                () -> emails.passwordReset(event.userId(), event.rawToken()));
    }

    private void dispatch(String kind, String target, UUID id, Supplier<Optional<EmailMessage>> compose) {
        try {
            executor.execute(() -> deliver(kind, target, id, compose));
        } catch (RuntimeException ex) {
            log.warn("{} email for {} {} was dropped: {}", kind, target, id, ex.getClass().getSimpleName());
        }
    }

    private void deliver(String kind, String target, UUID id, Supplier<Optional<EmailMessage>> compose) {
        try {
            var message = compose.get();
            if (message.isEmpty()) {
                log.info("{} email for {} {} skipped", kind, target, id);
                return;
            }
            sender.send(message.get());
            log.info("{} email sent for {} {} with {} attachment(s)", kind, target, id,
                    message.get().attachments().size());
        } catch (EmailDeliveryException ex) {
            log.warn("{} email for {} {} failed: {}", kind, target, id, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("{} email for {} {} could not be built", kind, target, id, ex);
        }
    }
}
