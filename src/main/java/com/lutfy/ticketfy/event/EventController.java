package com.lutfy.ticketfy.event;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.OpenApiExamples;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.PublicEndpoint;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events")
@Tag(name = ApiTags.EVENTS)
public class EventController {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final EventService service;
    private final EventCancellationService cancellationService;
    private final EventCalendarService calendarService;

    public EventController(EventService service, EventCancellationService cancellationService,
                           EventCalendarService calendarService) {
        this.service = service;
        this.cancellationService = cancellationService;
        this.calendarService = calendarService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Criar evento",
            description = "Cria o evento sem lotes; a venda começa quando houver um lote ativo "
                    + "(`POST /events/{eventId}/ticket-types`). `startsAt` e `endsAt` precisam estar no futuro.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "evento", summary = "Show com data de término",
                    value = OpenApiExamples.EVENT_REQUEST)))
    @ApiResponse(responseCode = "201", description = "Evento criado.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EventDetailsDTO.class),
                    examples = @ExampleObject(name = "evento", summary = "Evento criado",
                            value = OpenApiExamples.EVENT_RESPONSE)))
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.INVALID_EVENT_DATES})
    public ResponseEntity<EventDetailsDTO> create(@RequestBody @Valid EventCreationDTO dto, @AuthenticationPrincipal User organizer) {
        var event = service.create(dto, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping
    @PublicEndpoint
    @Operation(summary = "Buscar eventos",
            description = "Vitrine: eventos ativos, não cancelados e que ainda não terminaram, ordenados por "
                    + "`startsAt`. Os filtros se combinam.")
    public ResponseEntity<Page<EventSummaryDTO>> search(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Cidade exata, sem diferenciar maiúsculas.")
            @RequestParam(required = false) String city,
            @Parameter(description = "Uma ou mais categorias, repetindo o parâmetro ou separando por vírgula.")
            @RequestParam(required = false) List<EventCategory> category,
            @Parameter(description = "`true` traz só os destaques que ainda não começaram.")
            @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Filtra por esgotado (`true`) ou com ingressos (`false`).")
            @RequestParam(required = false) Boolean soldOut,
            @ParameterObject @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        var page = service.search(q, city, category, featured, soldOut, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/categories")
    @PublicEndpoint
    @Operation(summary = "Listar categorias",
            description = "Todas as categorias, com a quantidade de eventos visíveis na vitrine em cada uma.")
    public ResponseEntity<List<EventCategoryCountDTO>> categories() {
        return ResponseEntity.ok(service.countByCategory());
    }

    @GetMapping("/{id}")
    @PublicEndpoint
    @Operation(summary = "Ver evento", description = "Inclui eventos cancelados e encerrados; `salesClosed` diz "
            + "se ainda dá para comprar.")
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<EventDetailsDTO> findById(@PathVariable UUID id) {
        var event = service.findById(id);
        return ResponseEntity.ok(event);
    }

    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
    @PublicEndpoint
    @Operation(summary = "Baixar o evento para a agenda",
            description = "Arquivo iCalendar (.ics) para Google Agenda, Outlook ou Apple Calendar. Evento "
                    + "cancelado sai com `STATUS:CANCELLED`.")
    @ApiResponse(responseCode = "200", description = "Arquivo .ics.",
            headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION, description = ApiHeaders.ATTACHMENT),
            content = @Content(mediaType = "text/calendar", schema = @Schema(type = "string")))
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<String> calendar(@PathVariable UUID id) {
        var body = calendarService.calendar(id);
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("ticketfy-event-" + id + ".ics").build().toString())
                .body(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar evento",
            description = "Só o dono do evento ou um ADMIN. Campos ausentes ou `null` ficam como estão; "
                    + "`imageUrl` vazio remove a imagem.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.INVALID_EVENT_STATE, ProblemType.INVALID_EVENT_DATES})
    public ResponseEntity<EventDetailsDTO> update(@PathVariable UUID id,
                                                  @RequestBody @Valid EventUpdateDTO dto,
                                                  @AuthenticationPrincipal User requester) {
        var event = service.update(id, dto, requester);
        return ResponseEntity.ok(event);
    }

    @PatchMapping("/{id}/featured")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Destacar evento na vitrine")
    @ProblemResponses(ProblemType.EVENT_NOT_FOUND)
    public ResponseEntity<EventDetailsDTO> changeFeatured(@PathVariable UUID id,
                                                          @RequestBody @Valid EventFeaturedUpdateDTO dto) {
        return ResponseEntity.ok(service.changeFeatured(id, dto.featured()));
    }

    @GetMapping("/{id}/cancellation-preview")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Prever o cancelamento",
            description = "Mostra quantos pedidos seriam reembolsados ou cancelados, sem mudar nada. "
                    + "Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND})
    public ResponseEntity<EventCancellationPreviewDTO> cancellationPreview(@PathVariable UUID id,
                                                                           @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(cancellationService.preview(id, requester));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Cancelar evento",
            description = "Cancela o evento e processa os pedidos: pagos são reembolsados e pendentes, cancelados. "
                    + "O corpo, com o motivo, é opcional. Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.INVALID_EVENT_STATE})
    public ResponseEntity<EventCancellationResultDTO> cancel(@PathVariable UUID id,
                                                             @RequestBody(required = false) @Valid EventCancellationDTO dto,
                                                             @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(cancellationService.cancel(id, dto, requester));
    }

    @GetMapping("/mine")
    @Operation(summary = "Listar meus eventos", description = "Eventos criados pela conta, inclusive os encerrados.")
    public ResponseEntity<Page<EventSummaryDTO>> listMyEvents(@AuthenticationPrincipal User user,
            @ParameterObject @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        var events = service.listMyEvents(user.getId(), pageable);
        return ResponseEntity.ok(events);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir evento",
            description = "Só eventos sem ingressos vendidos ou reservados; com vendas, use o cancelamento. "
                    + "Só o dono do evento ou um ADMIN.")
    @ApiResponse(responseCode = "204", description = "Evento excluído.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND,
            ProblemType.INVALID_EVENT_STATE, ProblemType.EVENT_HAS_SALES})
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User requester) {
        service.delete(id, requester);
        return ResponseEntity.noContent().build();
    }

}
