package com.lutfy.ticketfy.infra.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return problems.response(ProblemType.EMAIL_ALREADY_EXISTS, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationErrorDTO> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorDTO(error.getField(), error.getDefaultMessage()))
                .toList();
        var problem = problems.create(ProblemType.VALIDATION_FAILED, "Validation failed");
        problem.setProperty("errors", errors);
        return problems.response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body", ex);
        return problems.response(ProblemType.MALFORMED_REQUEST_BODY, "Malformed request body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex) {
        return problems.response(ProblemType.MISSING_PARAMETER,
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        return problems.response(ProblemType.MISSING_HEADER,
                "Required header '" + ex.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return problems.response(ProblemType.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        return problems.response(ProblemType.INVALID_CREDENTIALS, "Invalid credentials");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problems.response(ProblemType.INTERNAL_ERROR, "Internal server error");
    }

    @ExceptionHandler(InvalidRoleChangeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRoleChange(InvalidRoleChangeException ex) {
        return problems.response(ProblemType.INVALID_ROLE_CHANGE, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException ex) {
        return problems.response(ProblemType.USER_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFound(EventNotFoundException ex) {
        return problems.response(ProblemType.EVENT_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EventAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(EventAccessDeniedException ex) {
        return problems.response(ProblemType.EVENT_ACCESS_DENIED, ex.getMessage());
    }

    @ExceptionHandler(InvalidEventDatesException.class)
    public ResponseEntity<ProblemDetail> handleInvalidEventDates(InvalidEventDatesException ex) {
        return problems.response(ProblemType.INVALID_EVENT_DATES, ex.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return problems.response(ProblemType.ACCESS_DENIED, "Access denied");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problems.response(ProblemType.INVALID_PARAMETER,
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handlePropertyReference(PropertyReferenceException ex) {
        return problems.response(ProblemType.INVALID_PARAMETER, "Invalid value for parameter 'sort'");
    }

    @ExceptionHandler(TicketTypeNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTicketTypeNotFound(TicketTypeNotFoundException ex) {
        return problems.response(ProblemType.TICKET_TYPE_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidTicketTypeQuantityException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTicketTypeQuantity(InvalidTicketTypeQuantityException ex) {
        return problems.response(ProblemType.INVALID_TICKET_TYPE_QUANTITY, ex.getMessage());
    }

    @ExceptionHandler(TicketTypeNameAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleTicketTypeNameAlreadyExists(TicketTypeNameAlreadyExistsException ex) {
        return problems.response(ProblemType.TICKET_TYPE_NAME_ALREADY_EXISTS, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderState(InvalidOrderStateException ex) {
        return problems.response(ProblemType.INVALID_ORDER_STATE, ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientStock(InsufficientStockException ex) {
        return problems.response(ProblemType.INSUFFICIENT_STOCK, ex.getMessage());
    }

    @ExceptionHandler(MixedEventsOrderException.class)
    public ResponseEntity<ProblemDetail> handleMixedEventsOrder(MixedEventsOrderException ex) {
        return problems.response(ProblemType.MIXED_EVENTS_ORDER, ex.getMessage());
    }

    @ExceptionHandler(MaxPerOrderExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxPorOrderExceeded(MaxPerOrderExceededException ex) {
        return problems.response(ProblemType.MAX_PER_ORDER_EXCEEDED, ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex) {
        return problems.response(ProblemType.ORDER_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPaymentState(InvalidPaymentStateException ex) {
        return problems.response(ProblemType.INVALID_PAYMENT_STATE, ex.getMessage());
    }

    @ExceptionHandler(InvalidTicketStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTicketState(InvalidTicketStateException ex) {
        return problems.response(ProblemType.INVALID_TICKET_STATE, ex.getMessage());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTicketNotFound(TicketNotFoundException ex) {
        return problems.response(ProblemType.TICKET_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return problems.response(ProblemType.RESOURCE_NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        var problem = problems.create(ProblemType.METHOD_NOT_ALLOWED, "Method not allowed");
        var allowed = ex.getSupportedHttpMethods();
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .allow(allowed == null ? new HttpMethod[0] : allowed.toArray(HttpMethod[]::new))
                .body(problem);
    }

    @ExceptionHandler(InvalidEventStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidEventState(InvalidEventStateException ex) {
        return problems.response(ProblemType.INVALID_EVENT_STATE, ex.getMessage());
    }

    @ExceptionHandler(EventHasSalesException.class)
    public ResponseEntity<ProblemDetail> handleEventHasSales(EventHasSalesException ex) {
        return problems.response(ProblemType.EVENT_HAS_SALES, ex.getMessage());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDateRange(InvalidDateRangeException ex) {
        return problems.response(ProblemType.INVALID_PARAMETER, ex.getMessage());
    }

    @ExceptionHandler(ProblemException.class)
    public ResponseEntity<ProblemDetail> handleProblem(ProblemException ex) {
        var problem = problems.create(ex.getType(), ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        return problems.response(problem);
    }

    @ExceptionHandler(TooManyTransferAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyTransferAttempts(TooManyTransferAttemptsException ex) {
        var problem = problems.create(ProblemType.TOO_MANY_TRANSFER_ATTEMPTS, ex.getMessage());
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(TooManyCouponAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyCouponAttempts(TooManyCouponAttemptsException ex) {
        var problem = problems.create(ProblemType.TOO_MANY_COUPON_ATTEMPTS, ex.getMessage());
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(TooManyPasswordAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyPasswordAttempts(TooManyPasswordAttemptsException ex) {
        var problem = problems.create(ProblemType.TOO_MANY_PASSWORD_ATTEMPTS, ex.getMessage());
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(TooManyDataExportsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyDataExports(TooManyDataExportsException ex) {
        var problem = problems.create(ProblemType.TOO_MANY_DATA_EXPORTS, ex.getMessage());
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }
}
