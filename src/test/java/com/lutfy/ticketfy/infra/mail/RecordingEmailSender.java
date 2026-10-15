package com.lutfy.ticketfy.infra.mail;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** EmailSender falso: guarda as mensagens em memória em vez de enviar. */
public class RecordingEmailSender implements EmailSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final List<EmailMessage> sent = new ArrayList<>();
    private volatile boolean failing;

    @Override
    public synchronized void send(EmailMessage message) {
        if (failing) {
            throw new EmailDeliveryException("Simulated delivery failure");
        }
        sent.add(message);
    }

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public synchronized List<EmailMessage> sentTo(String email) {
        return sent.stream().filter(message -> message.toEmail().equals(email)).toList();
    }

    /** Espera até chegarem {@code count} e-mails para o endereço e devolve todos eles. */
    public List<EmailMessage> await(String email, int count) {
        var deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            var messages = sentTo(email);
            if (messages.size() >= count) return messages;
            pause();
        }
        throw new AssertionError("Expected " + count + " email(s) to " + email + " but got " + sentTo(email).size());
    }

    /** Espera a fila de e-mails esvaziar (tudo que foi agendado terminou). */
    public static void awaitIdle(ThreadPoolTaskExecutor executor) {
        var pool = executor.getThreadPoolExecutor();
        var deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (pool.getCompletedTaskCount() == pool.getTaskCount()) return;
            pause();
        }
        throw new AssertionError("Mail executor did not become idle");
    }

    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
