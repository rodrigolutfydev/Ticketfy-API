package com.lutfy.ticketfy.payout.withdrawal;

import java.util.List;

public enum PayoutStatus {
    UNDER_REVIEW,
    REQUESTED,
    PROCESSING,
    PAID,
    FAILED,
    CANCELLED,
    REJECTED;

    private static final List<PayoutStatus> IN_PROGRESS = List.of(UNDER_REVIEW, REQUESTED, PROCESSING);

    public static List<PayoutStatus> inProgress() {
        return IN_PROGRESS;
    }
}
