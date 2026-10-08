package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService service;
    private final TicketTransferService transferService;

    public TicketController(TicketService service, TicketTransferService transferService) {
        this.service = service;
        this.transferService = transferService;
    }

    @GetMapping("/me")
    public ResponseEntity<Page<TicketDetailsDTO>> listMyTickets(
            @AuthenticationPrincipal User authenticated,
            Pageable pageable) {
        return ResponseEntity.ok(service.listMyTickets(authenticated, pageable));
    }

    @PostMapping("/{code}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<TicketDetailsDTO> checkIn(
            @PathVariable String code,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.checkIn(code, authenticated));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<TicketTransferResultDTO> transfer(
            @PathVariable UUID id,
            @RequestBody @Valid TicketTransferRequestDTO dto,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(transferService.transfer(id, dto, authenticated));
    }
}
