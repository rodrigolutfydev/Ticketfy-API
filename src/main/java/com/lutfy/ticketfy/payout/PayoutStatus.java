package com.lutfy.ticketfy.payout;

import java.util.List;

public enum PayoutStatus {
    REQUESTED,
    PROCESSING,
    PAID,
    FAILED,
    CANCELLED;

    private static final List<PayoutStatus> IN_PROGRESS = List.of(REQUESTED, PROCESSING);

    public static List<PayoutStatus> inProgress() {
        return IN_PROGRESS;
    }
}
