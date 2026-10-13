package com.lutfy.ticketfy.infra.exception;

import java.util.Map;

public record ValidationErrorDTO(String field, String message, String code, Map<String, Object> params) {
}
