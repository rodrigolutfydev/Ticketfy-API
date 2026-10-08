package com.lutfy.ticketfy.payout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class PayoutSettings {

    private final BigDecimal platformFeePercent;
    private final int releaseDelayDays;
    private final BigDecimal minAmount;
    private final Duration keyChangeCooldown;
    private final Duration stuckAfter;
    private final BigDecimal reviewThreshold;

    public PayoutSettings(@Value("${ticketfy.payout.platform-fee-percent}") BigDecimal platformFeePercent,
                          @Value("${ticketfy.payout.release-delay-days}") int releaseDelayDays,
                          @Value("${ticketfy.payout.min-amount}") BigDecimal minAmount,
                          @Value("${ticketfy.payout.key-change-cooldown-hours}") long keyChangeCooldownHours,
                          @Value("${ticketfy.payout.stuck-after-minutes}") long stuckAfterMinutes,
                          @Value("${ticketfy.payout.review-threshold}") BigDecimal reviewThreshold) {
        if (platformFeePercent.signum() < 0 || platformFeePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("ticketfy.payout.platform-fee-percent must be between 0 and 100");
        }
        if (platformFeePercent.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("ticketfy.payout.platform-fee-percent must have at most 2 decimal places");
        }
        if (releaseDelayDays < 0) {
            throw new IllegalStateException("ticketfy.payout.release-delay-days must not be negative");
        }
        if (minAmount.signum() <= 0 || minAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("ticketfy.payout.min-amount must be positive with at most 2 decimal places");
        }
        if (keyChangeCooldownHours < 0 || stuckAfterMinutes <= 0) {
            throw new IllegalStateException("ticketfy.payout cooldown must not be negative and stuck-after must be positive");
        }
        this.platformFeePercent = platformFeePercent;
        this.releaseDelayDays = releaseDelayDays;
        this.minAmount = minAmount.setScale(2);
        this.keyChangeCooldown = Duration.ofHours(keyChangeCooldownHours);
        this.stuckAfter = Duration.ofMinutes(stuckAfterMinutes);
        if (reviewThreshold.signum() < 0) {
            throw new IllegalStateException("ticketfy.payout.review-threshold must not be negative");
        }
        this.reviewThreshold = reviewThreshold.setScale(2);
    }

    public BigDecimal reviewThreshold() {
        return reviewThreshold;
    }

    public BigDecimal minAmount() {
        return minAmount;
    }

    public Duration keyChangeCooldown() {
        return keyChangeCooldown;
    }

    public Duration stuckAfter() {
        return stuckAfter;
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
