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
    private final EventCancellationService cancellationService;

    public EventController(EventService service, EventCancellationService cancellationService) {
        this.service = service;
        this.cancellationService = cancellationService;
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
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean soldOut,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        var page = service.search(q, city, featured, soldOut, pageable);
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

    @PatchMapping("/{id}/featured")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventDetailsDTO> changeFeatured(@PathVariable UUID id,
                                                          @RequestBody @Valid EventFeaturedUpdateDTO dto) {
        return ResponseEntity.ok(service.changeFeatured(id, dto.featured()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<EventCancellationResultDTO> cancel(@PathVariable UUID id,
                                                             @RequestBody(required = false) @Valid EventCancellationDTO dto,
                                                             @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(cancellationService.cancel(id, dto, requester));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<EventSummaryDTO>> listMyEvents(@AuthenticationPrincipal User user, @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        var events = service.listMyEvents(user.getId(), pageable);
        return ResponseEntity.ok(events);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User requester) {
        service.delete(id, requester);
        return ResponseEntity.noContent().build();
    }

}
