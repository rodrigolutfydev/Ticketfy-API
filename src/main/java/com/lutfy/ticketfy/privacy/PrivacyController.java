package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@Tag(name = ApiTags.PRIVACY)
public class PrivacyController {

    private final DataExportService exportService;
    private final AccountDeletionService deletionService;
    private final RefreshTokenCookie cookie;

    public PrivacyController(DataExportService exportService, AccountDeletionService deletionService,
                             RefreshTokenCookie cookie) {
        this.exportService = exportService;
        this.deletionService = deletionService;
        this.cookie = cookie;
    }

    @PostMapping("/data-export")
    @Operation(summary = "Exportar os próprios dados",
            description = "Devolve em JSON tudo o que a conta guarda: perfil, pedidos, ingressos, transferências "
                    + "e dados de organizador. Exige a senha. Limite: 3 exportações a cada 24 horas.")
    @ApiResponse(responseCode = "200", description = "Arquivo JSON com os dados.",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = ApiHeaders.ATTACHMENT))
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.INVALID_PASSWORD, ProblemType.PASSWORD_NOT_SET,
            ProblemType.TOO_MANY_PASSWORD_ATTEMPTS, ProblemType.TOO_MANY_DATA_EXPORTS})
    public ResponseEntity<DataExportDTO> export(@AuthenticationPrincipal User user,
                                                @RequestBody @Valid DataExportRequestDTO dto) {
        var export = exportService.export(user, dto);
        var filename = "ticketfy-meus-dados-" + exportService.today() + ".json";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(export);
    }

    @PostMapping("/deletion")
    @Operation(summary = "Excluir a conta",
            description = "Anonimiza a conta e encerra todas as sessões. Quem tem senha confirma com `password`; "
                    + "conta criada pelo Google confirma com `email`. Recusada para ADMIN, conta suspensa, saques "
                    + "bloqueados ou em andamento, saldo, eventos ativos com vendas e ingressos de eventos futuros.")
    @ApiResponse(responseCode = "204", description = "Conta excluída.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.CLEAR_REFRESH_COOKIE))
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.INVALID_PASSWORD,
            ProblemType.EMAIL_CONFIRMATION_MISMATCH, ProblemType.ACCOUNT_SUSPENDED,
            ProblemType.ADMIN_ACCOUNT_DELETION, ProblemType.ACCOUNT_HAS_PAYOUT_BLOCK,
            ProblemType.ACCOUNT_HAS_PAYOUT_IN_PROGRESS, ProblemType.ACCOUNT_HAS_BALANCE,
            ProblemType.ACCOUNT_HAS_ACTIVE_EVENTS, ProblemType.ACCOUNT_HAS_UPCOMING_TICKETS,
            ProblemType.TOO_MANY_PASSWORD_ATTEMPTS})
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                       @RequestBody @Valid AccountDeletionRequestDTO dto) {
        deletionService.delete(user, dto);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }
}
