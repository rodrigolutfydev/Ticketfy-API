package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
public class PayoutController {

    private final PayoutService service;
    private final PayoutRequestService requests;

    public PayoutController(PayoutService service, PayoutRequestService requests) {
        this.service = service;
        this.requests = requests;
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

    @PostMapping("/payouts")
    public ResponseEntity<PayoutDTO> requestPayout(
            @RequestBody @Valid PayoutRequestDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal User organizer) {
        PayoutDTO payout;
        try {
            payout = requests.request(organizer, dto, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            var existing = idempotencyKey == null
                    ? Optional.<PayoutDTO>empty()
                    : requests.findByIdempotencyKey(organizer, idempotencyKey);
            if (existing.isPresent()) {
                payout = existing.get();
            } else if (requests.hasPayoutInProgress(organizer)) {
                throw PayoutRequestService.inProgress();
            } else {
                throw ex;
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payout);
    }

    @GetMapping("/payouts")
    public ResponseEntity<Page<PayoutDTO>> listPayouts(@PageableDefault(size = 20) Pageable pageable,
                                                       @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(requests.list(organizer, pageable));
    }

    @PostMapping("/payouts/{id}/cancel")
    public ResponseEntity<PayoutDTO> cancelPayout(@PathVariable UUID id, @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(requests.cancel(organizer, id));
    }
}
