package com.lutfy.ticketfy.dashboard;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = ApiTags.ORGANIZER)
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/events/{id}/dashboard")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Painel do evento",
            description = "Vendas, receita, check-in, cupons e série diária (fuso America/Sao_Paulo). "
                    + "Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND})
    public ResponseEntity<EventDashboardDTO> eventDashboard(@PathVariable UUID id,
                                                            @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.eventDashboard(id, requester));
    }

    @GetMapping("/events/{id}/orders")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Pedidos do evento",
            description = "Pedidos do evento, do mais recente ao mais antigo (ignora `sort`). Só o dono do evento "
                    + "ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND})
    public ResponseEntity<Page<EventOrderDTO>> eventOrders(@PathVariable UUID id,
                                                           @RequestParam(required = false) OrderStatus status,
                                                           @Parameter(description = "Trecho do nome ou e-mail.")
                                                           @RequestParam(required = false) String q,
                                                           @ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                                           @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.eventOrders(id, status, q, pageable, requester));
    }

    @GetMapping("/organizer/dashboard")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Painel do organizador",
            description = "Totais de todos os eventos da conta e a lista paginada dos eventos com seus números.")
    public ResponseEntity<OrganizerDashboardDTO> organizerDashboard(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                                                    @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.organizerDashboard(pageable, requester));
    }
}
