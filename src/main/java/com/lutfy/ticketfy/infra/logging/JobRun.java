package com.lutfy.ticketfy.infra.logging;

import org.slf4j.MDC;

import java.util.UUID;

public final class JobRun {

    public static final String MDC_KEY = "runId";

    private JobRun() {
    }

    public static void run(Runnable job) {
        MDC.put(MDC_KEY, UUID.randomUUID().toString());
        try {
            job.run();
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
