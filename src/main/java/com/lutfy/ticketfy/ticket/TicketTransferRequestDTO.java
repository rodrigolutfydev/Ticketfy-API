package com.lutfy.ticketfy.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketTransferRequestDTO(
        @NotBlank @Email @Size(max = 150) String recipientEmail,
        @NotBlank @Size(max = 100) String password
) {
    @Override
    public String toString() {
        return "TicketTransferRequestDTO[]";
    }
}
