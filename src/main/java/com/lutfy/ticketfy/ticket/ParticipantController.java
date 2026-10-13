package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}")
public class ParticipantController {

    private final ParticipantService service;

    public ParticipantController(ParticipantService service) {
        this.service = service;
    }

    @GetMapping("/participants")
    public ResponseEntity<Page<ParticipantDTO>> list(@PathVariable UUID eventId,
                                                     @RequestParam(required = false) UUID ticketTypeId,
                                                     @RequestParam(required = false) TicketStatus status,
                                                     @RequestParam(required = false) String q,
                                                     @PageableDefault(size = 20) Pageable pageable,
                                                     @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.list(eventId, ticketTypeId, status, q, pageable, requester));
    }

    @GetMapping("/participants.csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID eventId,
                                            @RequestParam(required = false) UUID ticketTypeId,
                                            @RequestParam(required = false) TicketStatus status,
                                            @RequestParam(required = false) String q,
                                            @AuthenticationPrincipal User requester) {
        var csv = service.exportCsv(eventId, ticketTypeId, status, q, requester);
        var filename = ParticipantCsv.filename(service.today().toString(), requester.getLanguage());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
