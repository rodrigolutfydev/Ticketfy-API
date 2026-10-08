package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/coupons")
public class CouponController {

    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<List<CouponDetailsDTO>> list(@PathVariable UUID eventId,
                                                       @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.list(eventId, authenticated));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<CouponDetailsDTO> create(@PathVariable UUID eventId,
                                                   @RequestBody @Valid CouponCreationDTO dto,
                                                   @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(eventId, dto, authenticated));
    }

    @PutMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<CouponDetailsDTO> update(@PathVariable UUID eventId, @PathVariable UUID couponId,
                                                   @RequestBody @Valid CouponUpdateDTO dto,
                                                   @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.update(eventId, couponId, dto, authenticated));
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID couponId,
                                       @AuthenticationPrincipal User authenticated) {
        service.delete(eventId, couponId, authenticated);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    public ResponseEntity<CouponPreviewDTO> preview(@PathVariable UUID eventId,
                                                    @RequestBody @Valid CouponPreviewRequestDTO dto,
                                                    @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.preview(eventId, dto, authenticated));
    }
}
