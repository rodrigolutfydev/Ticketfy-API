package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@Tag(name = ApiTags.TICKETS)
public class TicketController {

    private final TicketService service;
    private final TicketTransferService transferService;

    public TicketController(TicketService service, TicketTransferService transferService) {
        this.service = service;
        this.transferService = transferService;
    }

    @GetMapping("/me")
    @Operation(summary = "Listar meus ingressos",
            description = "Ingressos de que a conta é dona hoje, inclusive os recebidos por transferência.")
    public ResponseEntity<Page<TicketDetailsDTO>> listMyTickets(
            @AuthenticationPrincipal User authenticated,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(service.listMyTickets(authenticated, pageable));
    }

    @PostMapping("/{code}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Fazer check-in",
            description = "Marca o ingresso do código lido como `USED`. Cada código passa uma vez: a segunda "
                    + "leitura responde 409 `invalid-ticket-state` com `reason` `already-used` e `usedAt`. "
                    + "Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.TICKET_NOT_FOUND,
            ProblemType.INVALID_TICKET_STATE})
    public ResponseEntity<TicketDetailsDTO> checkIn(
            @PathVariable String code,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.checkIn(code, authenticated));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Transferir ingresso",
            description = "Passa o ingresso para outra conta pelo e-mail, com confirmação de senha. O código "
                    + "antigo deixa de valer e um novo é gerado. Até 3 transferências por ingresso, só antes do "
                    + "início do evento.")
    @ProblemResponses({ProblemType.SELF_TRANSFER, ProblemType.INVALID_PASSWORD, ProblemType.PASSWORD_NOT_SET,
            ProblemType.TICKET_NOT_FOUND, ProblemType.INVALID_TICKET_STATE, ProblemType.INVALID_EVENT_STATE,
            ProblemType.TICKET_TRANSFER_CLOSED, ProblemType.TICKET_TRANSFER_LIMIT_REACHED,
            ProblemType.TRANSFER_RECIPIENT_UNAVAILABLE, ProblemType.TOO_MANY_PASSWORD_ATTEMPTS,
            ProblemType.TOO_MANY_TRANSFER_ATTEMPTS})
    public ResponseEntity<TicketTransferResultDTO> transfer(
            @PathVariable UUID id,
            @RequestBody @Valid TicketTransferRequestDTO dto,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(transferService.transfer(id, dto, authenticated));
    }
}
