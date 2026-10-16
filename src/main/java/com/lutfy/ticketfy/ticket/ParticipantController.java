package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}")
@Tag(name = ApiTags.TICKETS)
public class ParticipantController {

    private final ParticipantService service;

    public ParticipantController(ParticipantService service) {
        this.service = service;
    }

    @GetMapping("/participants")
    @Operation(summary = "Listar participantes",
            description = "Ingressos emitidos do evento, com o titular atual, para conferência na entrada. "
                    + "Só o dono do evento ou um ADMIN; para os demais, o evento responde 404.")
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<Page<ParticipantDTO>> list(@PathVariable UUID eventId,
                                                     @RequestParam(required = false) UUID ticketTypeId,
                                                     @RequestParam(required = false) TicketStatus status,
                                                     @Parameter(description = "Trecho do nome do titular.")
                                                     @RequestParam(required = false) String q,
                                                     @ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                                     @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(service.list(eventId, ticketTypeId, status, q, pageable, requester));
    }

    @GetMapping("/participants.csv")
    @Operation(summary = "Exportar participantes em CSV",
            description = "Mesmos filtros da listagem, sem paginação. Separador, decimais e datas seguem o idioma "
                    + "da conta. Só o dono do evento ou um ADMIN.")
    @ApiResponse(responseCode = "200", description = "Arquivo CSV em UTF-8.",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = ApiHeaders.ATTACHMENT),
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID eventId,
                                            @RequestParam(required = false) UUID ticketTypeId,
                                            @RequestParam(required = false) TicketStatus status,
                                            @RequestParam(required = false) String q,
                                            @AuthenticationPrincipal User requester) {
        var csv = service.exportCsv(eventId, ticketTypeId, status, q, requester);
        var filename = ParticipantCsv.filename(service.today().toString(), requester.getLanguage());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
