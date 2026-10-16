package com.lutfy.ticketfy.auth.passwordreset;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.PublicEndpoint;
import com.lutfy.ticketfy.infra.security.ClientAddressResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Em /auth para passar pelo mesmo proxy do front que o login (Origin e
// X-Ticketfy-Auth são exigidos pelo AuthOriginFilter).
@RestController
@RequestMapping("/auth/password-reset")
@Tag(name = ApiTags.AUTH)
public class PasswordResetController {

    private final PasswordResetService service;
    private final PasswordResetLimiter limiter;
    private final ClientAddressResolver clientAddress;

    public PasswordResetController(PasswordResetService service, PasswordResetLimiter limiter,
                                   ClientAddressResolver clientAddress) {
        this.service = service;
        this.limiter = limiter;
        this.clientAddress = clientAddress;
    }

    /** Sempre 202, exista a conta ou não. */
    @PostMapping("/request")
    @PublicEndpoint
    @Operation(summary = "Pedir a recuperação de senha",
            description = "Envia por e-mail um link de uso único, válido por 30 minutos. A resposta é a mesma "
                    + "exista a conta ou não. Limite: 10 pedidos por IP e 3 por e-mail a cada 15 minutos.")
    @ApiResponse(responseCode = "202", description = "Pedido aceito.")
    @ProblemResponses(ProblemType.TOO_MANY_PASSWORD_RESET_ATTEMPTS)
    public ResponseEntity<Void> request(@RequestBody @Valid PasswordResetRequestDTO dto, HttpServletRequest request) {
        limiter.acquireRequest(clientAddress.resolve(request), dto.email());
        service.request(dto.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/confirm")
    @PublicEndpoint
    @Operation(summary = "Definir a nova senha",
            description = "Usa o token do link para trocar a senha. Todas as sessões abertas são encerradas; "
                    + "a pessoa entra de novo pelo login. Limite: 10 tentativas por IP a cada 15 minutos.")
    @ApiResponse(responseCode = "204", description = "Senha trocada.")
    @ProblemResponses({ProblemType.PASSWORD_RESET_TOKEN_INVALID, ProblemType.PASSWORD_RESET_TOKEN_EXPIRED,
            ProblemType.PASSWORD_RESET_TOKEN_USED, ProblemType.TOO_MANY_PASSWORD_RESET_ATTEMPTS})
    public ResponseEntity<Void> confirm(@RequestBody @Valid PasswordResetConfirmDTO dto, HttpServletRequest request) {
        limiter.acquireConfirmation(clientAddress.resolve(request));
        service.confirm(dto.token(), dto.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
