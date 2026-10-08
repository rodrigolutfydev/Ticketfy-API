package com.lutfy.ticketfy.payout.admin;

import com.lutfy.ticketfy.payout.withdrawal.PayoutDTO;
import com.lutfy.ticketfy.payout.withdrawal.PayoutStatus;
import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final AdminPayoutService service;

    public AdminPayoutController(AdminPayoutService service) {
        this.service = service;
    }

    @GetMapping("/payouts")
    public ResponseEntity<Page<AdminPayoutDTO>> list(
            @RequestParam(defaultValue = "UNDER_REVIEW") PayoutStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.list(status, pageable));
    }

    @PostMapping("/payouts/{id}/approve")
    public ResponseEntity<PayoutDTO> approve(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.approve(id, admin));
    }

    @PostMapping("/payouts/{id}/reject")
    public ResponseEntity<PayoutDTO> reject(@PathVariable UUID id, @RequestBody @Valid ReasonDTO dto,
                                            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.reject(id, dto.reason(), admin));
    }

    @GetMapping("/organizers")
    public ResponseEntity<OrganizerPayoutStatusDTO> findOrganizer(@RequestParam String email) {
        return ResponseEntity.ok(service.findOrganizer(email));
    }

    @PostMapping("/organizers/{id}/payout-block")
    public ResponseEntity<OrganizerPayoutStatusDTO> block(@PathVariable UUID id, @RequestBody @Valid ReasonDTO dto,
                                                          @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.block(id, dto.reason(), admin));
    }

    @DeleteMapping("/organizers/{id}/payout-block")
    public ResponseEntity<OrganizerPayoutStatusDTO> unblock(@PathVariable UUID id) {
        return ResponseEntity.ok(service.unblock(id));
    }
}
