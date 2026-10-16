package com.lutfy.ticketfy.payout.account;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizer/payout-account")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = ApiTags.ORGANIZER)
public class PayoutAccountController {

    private final PayoutAccountService service;

    public PayoutAccountController(PayoutAccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Ver dados de recebimento", description = "Chave Pix, documento e titular cadastrados.")
    @ProblemResponses(ProblemType.PAYOUT_ACCOUNT_NOT_FOUND)
    public ResponseEntity<PayoutAccountDTO> find(@AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.find(organizer));
    }

    @PutMapping
    @Operation(summary = "Cadastrar ou trocar dados de recebimento",
            description = "Chave Pix e documento do titular, com confirmação de senha. A chave precisa estar no "
                    + "nome do organizador. Depois de uma troca, novos saques esperam 48 horas.")
    @ProblemResponses({ProblemType.PIX_KEY_NOT_FOUND, ProblemType.PIX_KEY_HOLDER_MISMATCH,
            ProblemType.SESSION_EXPIRED, ProblemType.INVALID_PASSWORD, ProblemType.PASSWORD_NOT_SET,
            ProblemType.TOO_MANY_PASSWORD_ATTEMPTS})
    public ResponseEntity<PayoutAccountDTO> save(@RequestBody @Valid PayoutAccountUpdateDTO dto,
                                                 @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.save(organizer, dto));
    }
}
