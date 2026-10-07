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
    ORDER_ACCESS_DENIED("order-access-denied", "Order access denied", HttpStatus.FORBIDDEN),
    INVALID_PAYMENT_STATE("invalid-payment-state", "Invalid payment state", HttpStatus.CONFLICT),
    INVALID_TICKET_STATE("invalid-ticket-state", "Invalid ticket state", HttpStatus.CONFLICT),
    TICKET_NOT_FOUND("ticket-not-found", "Ticket not found", HttpStatus.NOT_FOUND),
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
    INVALID_PAYOUT_STATE("invalid-payout-state", "Invalid payout state", HttpStatus.CONFLICT);

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
