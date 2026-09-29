package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
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
}