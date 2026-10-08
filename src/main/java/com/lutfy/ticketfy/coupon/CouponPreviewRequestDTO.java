package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CouponPreviewRequestDTO(
        @NotNull @Size(max = 100)
        String code,
        @NotEmpty
        List<@Valid OrderItemRequestDTO> items
) {
}
