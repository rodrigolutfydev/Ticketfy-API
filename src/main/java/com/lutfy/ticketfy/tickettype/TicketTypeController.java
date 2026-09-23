package com.lutfy.ticketfy.tickettype;

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
@RequestMapping("/events/{eventId}/ticket-types")
public class TicketTypeController {

    private final TicketTypeService service;

    public TicketTypeController(TicketTypeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TicketTypeSummaryDTO>> listByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(service.listByEvent(eventId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<TicketTypeDetailsDTO> create(@PathVariable UUID eventId, @RequestBody @Valid TicketTypeCreationDTO dto, @AuthenticationPrincipal User authenticated) {
        var created = service.create(eventId, dto, authenticated);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
