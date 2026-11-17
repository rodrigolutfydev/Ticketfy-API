package com.lutfy.ticketfy.payment.pix;

import com.lutfy.ticketfy.user.User;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders/{orderId}/payment/pix")
public class PixPaymentController {

    private final PixPaymentService service;

    public PixPaymentController(
            PixPaymentService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PixPaymentResponse> create(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User authenticated
    ) {
        var response = service.createPix(
                orderId,
                authenticated
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}