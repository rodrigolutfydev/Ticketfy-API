package com.lutfy.ticketfy.payout.account;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizer/payout-account")
@PreAuthorize("hasRole('ORGANIZER')")
public class PayoutAccountController {

    private final PayoutAccountService service;

    public PayoutAccountController(PayoutAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PayoutAccountDTO> find(@AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.find(organizer));
    }

    @PutMapping
    public ResponseEntity<PayoutAccountDTO> save(@RequestBody @Valid PayoutAccountUpdateDTO dto,
                                                 @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.save(organizer, dto));
    }
}
