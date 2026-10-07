package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.infra.logging.JobRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventCancellationJob {

    private static final Logger log = LoggerFactory.getLogger(EventCancellationJob.class);

    private final EventCancellationService service;

    public EventCancellationJob(EventCancellationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    public void processPendingOrders() {
        JobRun.run(this::process);
    }

    private void process() {
        int processed = service.processPendingOrders();
        if (processed > 0) {
            log.info("Processed {} orders of cancelled events", processed);
        }
    }
}
