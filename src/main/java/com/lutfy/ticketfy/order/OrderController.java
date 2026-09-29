package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderDetailsDTO> create(
            @RequestBody @Valid OrderCreationDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal User authenticated) {
        OrderDetailsDTO order;
        try {
            order = service.create(dto, idempotencyKey, authenticated);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            order = service.findByIdempotencyKey(idempotencyKey, authenticated)
                    .orElseThrow(() -> ex);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailsDTO> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.findById(id, authenticated));
    }

    @GetMapping
    public ResponseEntity<Page<OrderSummaryDTO>> listMyOrders(
            @AuthenticationPrincipal User authenticated,
            Pageable pageable) {
        return ResponseEntity.ok(service.listMyOrders(authenticated, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<OrderDetailsDTO> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.cancel(id, authenticated));
    }
}
