package com.lutfy.ticketfy.payout.admin;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.payout.withdrawal.PayoutDTO;
import com.lutfy.ticketfy.payout.withdrawal.PayoutStatus;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = ApiTags.ADMIN)
public class AdminPayoutController {

    private final AdminPayoutService service;

    public AdminPayoutController(AdminPayoutService service) {
        this.service = service;
    }

    @GetMapping("/payouts")
    @Operation(summary = "Listar saques por status", description = "Padrão: os que aguardam análise.")
    public ResponseEntity<Page<AdminPayoutDTO>> list(
            @RequestParam(defaultValue = "UNDER_REVIEW") PayoutStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.list(status, pageable));
    }

    @PostMapping("/payouts/{id}/approve")
    @Operation(summary = "Aprovar saque", description = "Só saques `UNDER_REVIEW`; o saque segue para o pagamento.")
    @ProblemResponses({ProblemType.PAYOUT_NOT_FOUND, ProblemType.INVALID_PAYOUT_STATE})
    public ResponseEntity<PayoutDTO> approve(@PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.approve(id, admin));
    }

    @PostMapping("/payouts/{id}/reject")
    @Operation(summary = "Recusar saque",
            description = "Só saques `UNDER_REVIEW`; o valor volta ao saldo do organizador. O motivo é obrigatório.")
    @ProblemResponses({ProblemType.PAYOUT_NOT_FOUND, ProblemType.INVALID_PAYOUT_STATE})
    public ResponseEntity<PayoutDTO> reject(@PathVariable UUID id, @RequestBody @Valid ReasonDTO dto,
                                            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.reject(id, dto.reason(), admin));
    }

    @GetMapping("/organizers")
    @Operation(summary = "Buscar organizador pelo e-mail", description = "Situação de saques e bloqueio.")
    @ProblemResponses(ProblemType.ORGANIZER_NOT_FOUND)
    public ResponseEntity<OrganizerPayoutStatusDTO> findOrganizer(@RequestParam String email) {
        return ResponseEntity.ok(service.findOrganizer(email));
    }

    @PostMapping("/organizers/{id}/payout-block")
    @Operation(summary = "Bloquear saques do organizador",
            description = "Impede novos saques até o desbloqueio. O motivo não aparece para o organizador.")
    @ProblemResponses({ProblemType.ORGANIZER_NOT_FOUND, ProblemType.INVALID_PAYOUT_BLOCK_STATE})
    public ResponseEntity<OrganizerPayoutStatusDTO> block(@PathVariable UUID id, @RequestBody @Valid ReasonDTO dto,
                                                          @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.block(id, dto.reason(), admin));
    }

    @DeleteMapping("/organizers/{id}/payout-block")
    @Operation(summary = "Desbloquear saques do organizador")
    @ProblemResponses({ProblemType.ORGANIZER_NOT_FOUND, ProblemType.INVALID_PAYOUT_BLOCK_STATE})
    public ResponseEntity<OrganizerPayoutStatusDTO> unblock(@PathVariable UUID id) {
        return ResponseEntity.ok(service.unblock(id));
    }
}
