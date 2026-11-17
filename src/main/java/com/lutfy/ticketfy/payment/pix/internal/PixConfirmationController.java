package com.lutfy.ticketfy.payment.pix.internal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
public class PixConfirmationController {

    private final PixConfirmationService confirmationService;

    public PixConfirmationController(
            PixConfirmationService confirmationService
    ) {
        this.confirmationService = confirmationService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<PixConfirmationResponse> confirm(
            @Valid @RequestBody PixConfirmationRequest request
    ) {
        PixConfirmationResponse response =
                confirmationService.confirm(request);

        return ResponseEntity.ok(response);
    }
}