package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<EventDetailsDTO> create(@RequestBody @Valid EventCreationDTO dto, @AuthenticationPrincipal User organizer) {
        var event = service.create(dto, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping
    public ResponseEntity<Page<EventSummaryDTO>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        var page = service.search(q, city, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailsDTO> findById(@PathVariable UUID id) {
        var event = service.findById(id);
        return ResponseEntity.ok(event);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDetailsDTO> update(@PathVariable UUID id,
                                                  @RequestBody @Valid EventUpdateDTO dto,
                                                  @AuthenticationPrincipal User requester) {
        var event = service.update(id, dto, requester);
        return ResponseEntity.ok(event);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User requester) {
        service.delete(id, requester);
        return ResponseEntity.noContent().build();
    }

}
