package com.lutfy.ticketfy.payout.withdrawal;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = ApiTags.ORGANIZER)
public class PayoutController {

    private final PayoutRequestService requests;

    public PayoutController(PayoutRequestService requests) {
        this.requests = requests;
    }

    @PostMapping("/payouts")
    @Operation(summary = "Pedir saque",
            description = "Saca do saldo disponível para a chave Pix cadastrada, com confirmação de senha. Mínimo "
                    + "de R$ 20,00 e um saque em andamento por vez. O primeiro saque e os acima de R$ 5.000,00 "
                    + "passam pela análise de um ADMIN (`UNDER_REVIEW`).")
    @ApiResponse(responseCode = "201", description = "Saque pedido, ou o mesmo saque de antes quando a "
            + "`Idempotency-Key` se repete.")
    @ProblemResponses({ProblemType.INVALID_PARAMETER, ProblemType.PAYOUT_BELOW_MINIMUM, ProblemType.SESSION_EXPIRED,
            ProblemType.INVALID_PASSWORD, ProblemType.PASSWORD_NOT_SET, ProblemType.PAYOUT_ACCOUNT_REQUIRED,
            ProblemType.PAYOUTS_BLOCKED, ProblemType.PAYOUT_ACCOUNT_COOLDOWN, ProblemType.PAYOUT_IN_PROGRESS,
            ProblemType.NEGATIVE_AVAILABLE_BALANCE, ProblemType.PAYOUT_EXCEEDS_AVAILABLE,
            ProblemType.TOO_MANY_PASSWORD_ATTEMPTS})
    public ResponseEntity<PayoutDTO> requestPayout(
            @RequestBody @Valid PayoutRequestDTO dto,
            @Parameter(description = "Opcional. Repetir a chave devolve o mesmo saque (1 a 100 caracteres).")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal User organizer) {
        PayoutDTO payout;
        try {
            payout = requests.request(organizer, dto, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            var existing = idempotencyKey == null
                    ? Optional.<PayoutDTO>empty()
                    : requests.findByIdempotencyKey(organizer, idempotencyKey);
            if (existing.isPresent()) {
                payout = existing.get();
            } else if (requests.hasPayoutInProgress(organizer)) {
                throw PayoutRequestService.inProgress();
            } else {
                throw ex;
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payout);
    }

    @GetMapping("/payouts")
    @Operation(summary = "Listar saques", description = "Do mais recente ao mais antigo (ignora `sort`).")
    public ResponseEntity<Page<PayoutDTO>> listPayouts(@ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                                       @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(requests.list(organizer, pageable));
    }

    @PostMapping("/payouts/{id}/cancel")
    @Operation(summary = "Cancelar saque",
            description = "Só saques `REQUESTED` ou `UNDER_REVIEW`; o valor volta ao saldo.")
    @ProblemResponses({ProblemType.PAYOUT_NOT_FOUND, ProblemType.INVALID_PAYOUT_STATE})
    public ResponseEntity<PayoutDTO> cancelPayout(@PathVariable UUID id, @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(requests.cancel(organizer, id));
    }
}
