package com.lutfy.ticketfy.infra.exception;

import java.util.List;

public record ValidationErrorResponseDTO(String message, List<ValidationErrorDTO> errors) {
}
