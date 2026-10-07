package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/events/{id}/dashboard")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<EventDashboardDTO> eventDashboard(@PathVariable UUID id,
                                                            @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.eventDashboard(id, requester));
    }

    @GetMapping("/events/{id}/orders")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<Page<EventOrderDTO>> eventOrders(@PathVariable UUID id,
                                                           @RequestParam(required = false) OrderStatus status,
                                                           @RequestParam(required = false) String q,
                                                           @PageableDefault(size = 20) Pageable pageable,
                                                           @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.eventOrders(id, status, q, pageable, requester));
    }

    @GetMapping("/organizer/dashboard")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<OrganizerDashboardDTO> organizerDashboard(@PageableDefault(size = 20) Pageable pageable,
                                                                    @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.organizerDashboard(pageable, requester));
    }
}
