package com.lutfy.ticketfy.payout.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonDTO(@NotBlank @Size(max = 500) String reason) {
}
