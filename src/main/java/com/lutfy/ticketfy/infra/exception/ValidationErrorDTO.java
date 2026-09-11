package com.lutfy.ticketfy.infra.exception;

public record ValidationErrorDTO(String field, String message) {
}
