package com.lutfy.ticketfy.auth;

import com.lutfy.ticketfy.infra.logging.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "ticketfy.auth.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionCleanupJob.class);
    private static final int BATCH_SIZE = 500;

    private final AuthSessionService service;
    private final Duration retention;

    public AuthSessionCleanupJob(AuthSessionService service,
                                 @Value("${ticketfy.auth.cleanup.retention-days}") long retentionDays) {
        this.service = service;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT24H")
    public void deleteFinishedSessions() {
        JobRun.run(this::deleteAll);
    }

    int deleteAll() {
        int total = 0;
        int deleted;
        do {
            deleted = service.deleteFinished(retention, BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);
        if (total > 0) {
            log.info("Deleted {} finished sessions", total);
        }
        return total;
    }
}
