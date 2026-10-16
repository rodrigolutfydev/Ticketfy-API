package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.PublicEndpoint;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = ApiTags.TICKET_TYPES)
public class TicketTypeController {

    private final TicketTypeService service;

    public TicketTypeController(TicketTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PublicEndpoint
    @Operation(summary = "Listar lotes à venda",
            description = "Lotes ativos do evento, com preço e situação do estoque (`soldOut` e, quando restam "
                    + "de 1 a 10, `remaining`).")
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<List<TicketTypeSummaryDTO>> listByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(service.listByEvent(eventId));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Listar lotes para gestão",
            description = "Todos os lotes, inclusive desativados, com vendidos e disponíveis. Só o dono do evento "
                    + "ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND})
    public ResponseEntity<List<TicketTypeDetailsDTO>> listForManagement(@PathVariable UUID eventId, @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.listForManagement(eventId, authenticated));
    }

    @PatchMapping("/{ticketTypeId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Editar lote",
            description = "Campos ausentes ficam como estão. `quantityTotal` não pode ficar abaixo do vendido mais "
                    + "o reservado. Mudar o preço não altera pedidos já feitos. Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.INVALID_TICKET_TYPE_QUANTITY, ProblemType.EVENT_ACCESS_DENIED,
            ProblemType.EVENT_NOT_FOUND, ProblemType.TICKET_TYPE_NOT_FOUND, ProblemType.INVALID_EVENT_STATE,
            ProblemType.TICKET_TYPE_NAME_ALREADY_EXISTS})
    public ResponseEntity<TicketTypeDetailsDTO> update(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId,
                                                       @RequestBody @Valid TicketTypeUpdateDTO dto,
                                                       @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.update(eventId, ticketTypeId, dto, authenticated));
    }

    @PostMapping("/{ticketTypeId}/deactivate")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Desativar lote",
            description = "Tira o lote da venda; ingressos já vendidos continuam válidos. Só o dono do evento ou um "
                    + "ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.TICKET_TYPE_NOT_FOUND, ProblemType.INVALID_EVENT_STATE, ProblemType.INVALID_TICKET_TYPE_STATE})
    public ResponseEntity<TicketTypeDetailsDTO> deactivate(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId,
                                                           @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.deactivate(eventId, ticketTypeId, authenticated));
    }

    @PostMapping("/{ticketTypeId}/reactivate")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Reativar lote",
            description = "Volta o lote à venda, só antes do início do evento. Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.TICKET_TYPE_NOT_FOUND, ProblemType.INVALID_EVENT_STATE, ProblemType.INVALID_TICKET_TYPE_STATE,
            ProblemType.SALES_CLOSED})
    public ResponseEntity<TicketTypeDetailsDTO> reactivate(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId,
                                                           @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.reactivate(eventId, ticketTypeId, authenticated));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Criar lote",
            description = "O nome é único dentro do evento. Só o dono do evento ou um ADMIN.")
    @ApiResponse(responseCode = "201", description = "Lote criado.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.INVALID_EVENT_STATE, ProblemType.TICKET_TYPE_NAME_ALREADY_EXISTS})
    public ResponseEntity<TicketTypeDetailsDTO> create(@PathVariable UUID eventId, @RequestBody @Valid TicketTypeCreationDTO dto, @AuthenticationPrincipal User authenticated) {
        var created = service.create(eventId, dto, authenticated);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
