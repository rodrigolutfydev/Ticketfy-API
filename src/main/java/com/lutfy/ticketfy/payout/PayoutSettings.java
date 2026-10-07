package com.lutfy.ticketfy.payout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class PayoutSettings {

    private final BigDecimal platformFeePercent;
    private final int releaseDelayDays;

    public PayoutSettings(@Value("${ticketfy.payout.platform-fee-percent}") BigDecimal platformFeePercent,
                          @Value("${ticketfy.payout.release-delay-days}") int releaseDelayDays) {
        if (platformFeePercent.signum() < 0 || platformFeePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("ticketfy.payout.platform-fee-percent must be between 0 and 100");
        }
        if (platformFeePercent.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("ticketfy.payout.platform-fee-percent must have at most 2 decimal places");
        }
        if (releaseDelayDays < 0) {
            throw new IllegalStateException("ticketfy.payout.release-delay-days must not be negative");
        }
        this.platformFeePercent = platformFeePercent;
        this.releaseDelayDays = releaseDelayDays;
    }

    public BigDecimal platformFeePercent() {
        return platformFeePercent;
    }

    public int releaseDelayDays() {
        return releaseDelayDays;
    }

    public Duration releaseDelay() {
        return Duration.ofDays(releaseDelayDays);
    }
}
