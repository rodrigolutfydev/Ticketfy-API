package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceDTO> balance(@AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.balance(organizer));
    }

    @GetMapping("/ledger")
    public ResponseEntity<Page<LedgerEntryDTO>> ledger(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.ledger(organizer, eventId, from, to, pageable));
    }
}
