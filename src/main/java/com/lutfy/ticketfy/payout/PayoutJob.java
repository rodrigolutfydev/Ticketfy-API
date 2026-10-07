package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.infra.logging.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketfy.payout.job.enabled", havingValue = "true", matchIfMissing = true)
public class PayoutJob {

    private static final Logger log = LoggerFactory.getLogger(PayoutJob.class);

    private final PayoutProcessingService service;

    public PayoutJob(PayoutProcessingService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    public void processPayouts() {
        JobRun.run(this::process);
    }

    private void process() {
        int finished = service.processAll();
        if (finished > 0) {
            log.info("Finished {} payouts", finished);
        }
    }
}
