package com.lutfy.ticketfy.infra.exception;

public class InvalidRoleChangeException extends RuntimeException {
    public InvalidRoleChangeException(String message) {
        super(message);
    }
}
