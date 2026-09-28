package com.lutfy.ticketfy.payment;

import com.lutfy.ticketfy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders/{orderId}/payment")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentDetailsDTO> pay(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User authenticated) {
        var payment = service.paySimulated(orderId, authenticated);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
}