package com.lutfy.ticketfy.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/{orderId}/payment")
public class PaymentController {

    @PostMapping
    public ResponseEntity<Void> pay() {
        return ResponseEntity
                .status(HttpStatus.GONE)
                .build();
    }
}