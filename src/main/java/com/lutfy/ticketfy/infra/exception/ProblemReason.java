package com.lutfy.ticketfy.infra.exception;

public enum ProblemReason {

    COUPON_PERCENT_OUT_OF_RANGE(ProblemType.INVALID_COUPON_SETTINGS, "percent-out-of-range"),
    COUPON_ENDS_BEFORE_STARTS(ProblemType.INVALID_COUPON_SETTINGS, "ends-before-starts"),
    COUPON_MAX_USES_BELOW_USED(ProblemType.INVALID_COUPON_SETTINGS, "max-uses-below-used"),
    COUPON_DISCOUNT_LOCKED(ProblemType.COUPON_ALREADY_USED, "discount-locked"),
    COUPON_DELETE_BLOCKED(ProblemType.COUPON_ALREADY_USED, "delete-blocked"),
    TICKET_TYPE_BELOW_SOLD(ProblemType.INVALID_TICKET_TYPE_QUANTITY, "below-sold"),
    TICKET_TYPE_ALREADY_ACTIVE(ProblemType.INVALID_TICKET_TYPE_STATE, "already-active"),
    TICKET_TYPE_ALREADY_INACTIVE(ProblemType.INVALID_TICKET_TYPE_STATE, "already-inactive"),
    EVENT_CANCELLED(ProblemType.INVALID_EVENT_STATE, "cancelled"),
    EVENT_ENDED(ProblemType.INVALID_EVENT_STATE, "ended"),
    ORDER_EXPIRED(ProblemType.INVALID_ORDER_STATE, "expired"),
    ORDER_REFUND_PERIOD_ENDED(ProblemType.INVALID_ORDER_STATE, "refund-period-ended"),
    ORDER_TICKETS_USED(ProblemType.INVALID_ORDER_STATE, "tickets-used"),
    ORDER_NOT_PENDING(ProblemType.INVALID_ORDER_STATE, "not-pending"),
    ORDER_NOT_PAID(ProblemType.INVALID_ORDER_STATE, "not-paid"),
    PAYMENT_ALREADY_PAID(ProblemType.INVALID_PAYMENT_STATE, "already-paid"),
    PAYMENT_NOT_PENDING(ProblemType.INVALID_PAYMENT_STATE, "not-pending"),
    PAYMENT_NOT_APPROVED(ProblemType.INVALID_PAYMENT_STATE, "not-approved"),
    TICKET_EVENT_CANCELLED(ProblemType.INVALID_TICKET_STATE, "event-cancelled"),
    TICKET_NOT_VALID(ProblemType.INVALID_TICKET_STATE, "not-valid"),
    TICKET_TRANSFER_UNAVAILABLE(ProblemType.INVALID_TICKET_STATE, "transfer-unavailable"),
    ACCOUNT_ALREADY_SUSPENDED(ProblemType.INVALID_SUSPENSION_STATE, "already-suspended"),
    ACCOUNT_NOT_SUSPENDED(ProblemType.INVALID_SUSPENSION_STATE, "not-suspended"),
    PAYOUT_ALREADY_PROCESSING(ProblemType.INVALID_PAYOUT_STATE, "already-processing"),
    PAYOUT_NOT_CANCELLABLE(ProblemType.INVALID_PAYOUT_STATE, "not-cancellable"),
    PAYOUT_NOT_UNDER_REVIEW(ProblemType.INVALID_PAYOUT_STATE, "not-under-review"),
    PAYOUT_CHANGED_CONCURRENTLY(ProblemType.INVALID_PAYOUT_STATE, "changed-concurrently"),
    PAYOUTS_ALREADY_BLOCKED(ProblemType.INVALID_PAYOUT_BLOCK_STATE, "already-blocked"),
    PAYOUTS_NOT_BLOCKED(ProblemType.INVALID_PAYOUT_BLOCK_STATE, "not-blocked");

    public static final String PROPERTY = "reason";

    private final ProblemType type;
    private final String value;

    ProblemReason(ProblemType type, String value) {
        this.type = type;
        this.value = value;
    }

    public ProblemType type() {
        return type;
    }

    public String value() {
        return value;
    }
}
