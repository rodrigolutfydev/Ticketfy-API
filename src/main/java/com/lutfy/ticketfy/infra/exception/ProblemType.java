package com.lutfy.ticketfy.infra.exception;

import org.springframework.http.HttpStatus;

public enum ProblemType {

    EMAIL_ALREADY_EXISTS("email-already-exists", "Email already exists", HttpStatus.CONFLICT),
    VALIDATION_FAILED("validation-failed", "Validation failed", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST_BODY("malformed-request-body", "Malformed request body", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER("missing-parameter", "Missing parameter", HttpStatus.BAD_REQUEST),
    MISSING_HEADER("missing-header", "Missing header", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("bad-request", "Bad request", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INVALID_CREDENTIALS("invalid-credentials", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_REQUIRED("authentication-required", "Authentication required", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED("session-expired", "Session expired", HttpStatus.UNAUTHORIZED),
    AUTH_REQUEST_REJECTED("auth-request-rejected", "Auth request rejected", HttpStatus.FORBIDDEN),
    ACCESS_DENIED("access-denied", "Access denied", HttpStatus.FORBIDDEN),
    TOO_MANY_LOGIN_ATTEMPTS("too-many-login-attempts", "Too many login attempts", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("internal-error", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_ROLE_CHANGE("invalid-role-change", "Invalid role change", HttpStatus.CONFLICT),
    USER_NOT_FOUND("user-not-found", "User not found", HttpStatus.NOT_FOUND),
    EVENT_NOT_FOUND("event-not-found", "Event not found", HttpStatus.NOT_FOUND),
    EVENT_ACCESS_DENIED("event-access-denied", "Event access denied", HttpStatus.FORBIDDEN),
    INVALID_EVENT_DATES("invalid-event-dates", "Invalid event dates", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("invalid-parameter", "Invalid parameter", HttpStatus.BAD_REQUEST),
    TICKET_TYPE_NOT_FOUND("ticket-type-not-found", "Ticket type not found", HttpStatus.NOT_FOUND),
    INVALID_TICKET_TYPE_QUANTITY("invalid-ticket-type-quantity", "Invalid ticket type quantity", HttpStatus.BAD_REQUEST),
    TICKET_TYPE_NAME_ALREADY_EXISTS("ticket-type-name-already-exists", "Ticket type name already exists", HttpStatus.CONFLICT),
    INVALID_ORDER_STATE("invalid-order-state", "Invalid order state", HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK("insufficient-stock", "Insufficient stock", HttpStatus.CONFLICT),
    MIXED_EVENTS_ORDER("mixed-events-order", "Mixed events order", HttpStatus.BAD_REQUEST),
    MAX_PER_ORDER_EXCEEDED("max-per-order-exceeded", "Max per order exceeded", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND("order-not-found", "Order not found", HttpStatus.NOT_FOUND),
    ORDER_HAS_TRANSFERRED_TICKETS("order-has-transferred-tickets", "Order has transferred tickets", HttpStatus.CONFLICT),
    INVALID_PAYMENT_STATE("invalid-payment-state", "Invalid payment state", HttpStatus.CONFLICT),
    INVALID_TICKET_STATE("invalid-ticket-state", "Invalid ticket state", HttpStatus.CONFLICT),
    TICKET_NOT_FOUND("ticket-not-found", "Ticket not found", HttpStatus.NOT_FOUND),
    TICKET_TRANSFER_CLOSED("ticket-transfer-closed", "Ticket transfer closed", HttpStatus.CONFLICT),
    TICKET_TRANSFER_LIMIT_REACHED("ticket-transfer-limit-reached", "Ticket transfer limit reached", HttpStatus.CONFLICT),
    SELF_TRANSFER("self-transfer", "Self transfer", HttpStatus.BAD_REQUEST),
    TRANSFER_RECIPIENT_UNAVAILABLE("transfer-recipient-unavailable", "Transfer recipient unavailable", HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_TRANSFER_ATTEMPTS("too-many-transfer-attempts", "Too many transfer attempts", HttpStatus.TOO_MANY_REQUESTS),
    RESOURCE_NOT_FOUND("resource-not-found", "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("method-not-allowed", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    INVALID_EVENT_STATE("invalid-event-state", "Invalid event state", HttpStatus.CONFLICT),
    EVENT_HAS_SALES("event-has-sales", "Event has sales", HttpStatus.CONFLICT),
    INVALID_PASSWORD("invalid-password", "Invalid password", HttpStatus.FORBIDDEN),
    TOO_MANY_PASSWORD_ATTEMPTS("too-many-password-attempts", "Too many password attempts", HttpStatus.TOO_MANY_REQUESTS),
    PAYOUT_ACCOUNT_NOT_FOUND("payout-account-not-found", "Payout account not found", HttpStatus.NOT_FOUND),
    PAYOUT_ACCOUNT_REQUIRED("payout-account-required", "Payout account required", HttpStatus.CONFLICT),
    PAYOUT_ACCOUNT_COOLDOWN("payout-account-cooldown", "Payout account cooldown", HttpStatus.CONFLICT),
    PIX_KEY_NOT_FOUND("pix-key-not-found", "Pix key not found", HttpStatus.BAD_REQUEST),
    PIX_KEY_HOLDER_MISMATCH("pix-key-holder-mismatch", "Pix key holder mismatch", HttpStatus.BAD_REQUEST),
    PAYOUT_IN_PROGRESS("payout-in-progress", "Payout in progress", HttpStatus.CONFLICT),
    NEGATIVE_AVAILABLE_BALANCE("negative-available-balance", "Negative available balance", HttpStatus.CONFLICT),
    PAYOUT_BELOW_MINIMUM("payout-below-minimum", "Payout below minimum", HttpStatus.BAD_REQUEST),
    PAYOUT_EXCEEDS_AVAILABLE("payout-exceeds-available", "Payout exceeds available balance", HttpStatus.CONFLICT),
    PAYOUT_NOT_FOUND("payout-not-found", "Payout not found", HttpStatus.NOT_FOUND),
    INVALID_PAYOUT_STATE("invalid-payout-state", "Invalid payout state", HttpStatus.CONFLICT),
    PAYOUTS_BLOCKED("payouts-blocked", "Payouts blocked", HttpStatus.CONFLICT),
    INVALID_PAYOUT_BLOCK_STATE("invalid-payout-block-state", "Invalid payout block state", HttpStatus.CONFLICT),
    ORGANIZER_NOT_FOUND("organizer-not-found", "Organizer not found", HttpStatus.NOT_FOUND),
    COUPON_NOT_FOUND("coupon-not-found", "Coupon not found", HttpStatus.NOT_FOUND),
    COUPON_CODE_ALREADY_EXISTS("coupon-code-already-exists", "Coupon code already exists", HttpStatus.CONFLICT),
    COUPON_ALREADY_USED("coupon-already-used", "Coupon already used", HttpStatus.CONFLICT),
    INVALID_COUPON_SETTINGS("invalid-coupon-settings", "Invalid coupon settings", HttpStatus.BAD_REQUEST),
    INVALID_COUPON("invalid-coupon", "Invalid coupon", HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_COUPON_ATTEMPTS("too-many-coupon-attempts", "Too many coupon attempts", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_NOT_ALLOWED("email-not-allowed", "Email not allowed", HttpStatus.BAD_REQUEST),
    TOO_MANY_DATA_EXPORTS("too-many-data-exports", "Too many data exports", HttpStatus.TOO_MANY_REQUESTS),
    ADMIN_ACCOUNT_DELETION("admin-account-deletion", "Admin account deletion", HttpStatus.CONFLICT),
    ACCOUNT_HAS_PAYOUT_BLOCK("account-has-payout-block", "Account has payout block", HttpStatus.CONFLICT),
    ACCOUNT_HAS_PAYOUT_IN_PROGRESS("account-has-payout-in-progress", "Account has payout in progress", HttpStatus.CONFLICT),
    ACCOUNT_HAS_BALANCE("account-has-balance", "Account has balance", HttpStatus.CONFLICT),
    ACCOUNT_HAS_ACTIVE_EVENTS("account-has-active-events", "Account has active events", HttpStatus.CONFLICT),
    ACCOUNT_HAS_UPCOMING_TICKETS("account-has-upcoming-tickets", "Account has upcoming tickets", HttpStatus.CONFLICT);

    private final String slug;
    private final String title;
    private final HttpStatus status;

    ProblemType(String slug, String title, HttpStatus status) {
        this.slug = slug;
        this.title = title;
        this.status = status;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }
}
