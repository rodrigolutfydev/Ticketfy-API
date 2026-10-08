package com.lutfy.ticketfy.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataExportRequestDTO(@NotBlank @Size(max = 100) String password) {

    @Override
    public String toString() {
        return "DataExportRequestDTO[]";
    }
}
