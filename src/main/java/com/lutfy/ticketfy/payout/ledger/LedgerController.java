package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/organizer")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = ApiTags.ORGANIZER)
public class LedgerController {

    private final BalanceService service;

    public LedgerController(BalanceService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    @Operation(summary = "Ver saldo",
            description = "Saldo pendente (vendas ainda no prazo de liberação), disponível para saque, retido "
                    + "(eventos cancelados) e total.")
    public ResponseEntity<BalanceDTO> balance(@AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.balance(organizer));
    }

    @GetMapping("/ledger")
    @Operation(summary = "Ver extrato",
            description = "Lançamentos de vendas, reembolsos e saques, do mais recente ao mais antigo "
                    + "(ignora `sort`).")
    public ResponseEntity<Page<LedgerEntryDTO>> ledger(
            @RequestParam(required = false) UUID eventId,
            @Parameter(description = "Primeiro dia (AAAA-MM-DD, fuso America/Sao_Paulo).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último dia, inclusive. Não pode ser anterior a `from`.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(service.ledger(organizer, eventId, from, to, pageable));
    }

    @GetMapping("/ledger.csv")
    @Operation(summary = "Exportar extrato em CSV",
            description = "Mesmos filtros do extrato, sem paginação. Separador, decimais e datas seguem o idioma "
                    + "da conta.")
    @ApiResponse(responseCode = "200", description = "Arquivo CSV em UTF-8.",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = ApiHeaders.ATTACHMENT),
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
    public ResponseEntity<byte[]> exportLedger(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User organizer) {
        var csv = service.exportLedger(organizer, eventId, from, to);
        var filename = LedgerCsv.filename(service.today().toString(), organizer.getLanguage());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
