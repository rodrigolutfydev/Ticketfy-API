package com.lutfy.ticketfy.payout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PayoutProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PayoutProcessingService.class);

    private final PayoutTransitions transitions;
    private final PayoutGateway gateway;

    public PayoutProcessingService(PayoutTransitions transitions, PayoutGateway gateway) {
        this.transitions = transitions;
        this.gateway = gateway;
    }

    public int processAll() {
        int finished = 0;
        for (var payoutId : transitions.requestedIds()) {
            if (process(payoutId)) finished++;
        }
        for (var payoutId : transitions.stuckIds()) {
            if (resume(payoutId)) finished++;
        }
        return finished;
    }

    boolean process(UUID payoutId) {
        try {
            var request = transitions.start(payoutId);
            if (request.isEmpty()) return false;
            return finish(payoutId, gateway.transfer(request.get()));
        } catch (RuntimeException ex) {
            log.error("Payout {} could not be transferred and will be resumed", payoutId, ex);
            return false;
        }
    }

    boolean resume(UUID payoutId) {
        try {
            var request = transitions.resumable(payoutId);
            if (request.isEmpty()) return false;
            var known = gateway.findTransfer(request.get().idempotencyKey());
            var result = known.isPresent() ? known.get() : gateway.transfer(request.get());
            return finish(payoutId, result);
        } catch (RuntimeException ex) {
            log.error("Payout {} could not be resumed and will be retried", payoutId, ex);
            return false;
        }
    }

    private boolean finish(UUID payoutId, PayoutGateway.TransferResult result) {
        var status = transitions.finish(payoutId, result);
        if (status == PayoutStatus.FAILED) {
            log.warn("Payout {} failed and was reversed", payoutId);
        }
        return status == PayoutStatus.PAID || status == PayoutStatus.FAILED;
    }
}
