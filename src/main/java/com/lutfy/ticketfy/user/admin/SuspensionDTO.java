package com.lutfy.ticketfy.user.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspensionDTO(@NotBlank @Size(max = 500) String reason) {
}
