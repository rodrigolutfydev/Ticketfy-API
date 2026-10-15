package com.lutfy.ticketfy.infra.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MailConfig {

    public static final String EXECUTOR = "mailExecutor";

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public EmailSender emailSender(MailSettings settings) {
        if (settings.brevoEnabled()) {
            return new BrevoEmailSender(settings);
        }
        log.warn("Brevo API key not set: transactional emails will only be logged");
        return new LoggingEmailSender();
    }

    // Fila própria e limitada: e-mail lento ou fora do ar nunca segura a
    // requisição nem os outros pools. Copia o MDC (requestId) para o log.
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor mailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.setTaskDecorator(task -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        return executor;
    }
}
