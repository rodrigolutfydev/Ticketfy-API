package com.lutfy.ticketfy.coupon;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
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
@RequestMapping("/events/{eventId}/coupons")
@Tag(name = ApiTags.COUPONS)
public class CouponController {

    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Listar cupons do evento",
            description = "Com usos, pedidos pagos e desconto concedido. Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND})
    public ResponseEntity<List<CouponDetailsDTO>> list(@PathVariable UUID eventId,
                                                       @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.list(eventId, authenticated));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Criar cupom",
            description = "Desconto percentual ou em valor fixo, com limites opcionais de usos, de usos por "
                    + "comprador e de período. O código é único no evento e não muda depois. Só o dono do evento "
                    + "ou um ADMIN.")
    @ApiResponse(responseCode = "201", description = "Cupom criado.")
    @ProblemResponses({ProblemType.INVALID_COUPON_SETTINGS, ProblemType.EVENT_ACCESS_DENIED,
            ProblemType.EVENT_NOT_FOUND, ProblemType.INVALID_EVENT_STATE, ProblemType.COUPON_CODE_ALREADY_EXISTS})
    public ResponseEntity<CouponDetailsDTO> create(@PathVariable UUID eventId,
                                                   @RequestBody @Valid CouponCreationDTO dto,
                                                   @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(eventId, dto, authenticated));
    }

    @PutMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Editar cupom",
            description = "Substitui todas as configurações. Depois do primeiro uso, tipo e valor do desconto "
                    + "ficam fixos. Só o dono do evento ou um ADMIN.")
    @ProblemResponses({ProblemType.INVALID_COUPON_SETTINGS, ProblemType.EVENT_ACCESS_DENIED,
            ProblemType.EVENT_NOT_FOUND, ProblemType.COUPON_NOT_FOUND, ProblemType.INVALID_EVENT_STATE,
            ProblemType.COUPON_ALREADY_USED})
    public ResponseEntity<CouponDetailsDTO> update(@PathVariable UUID eventId, @PathVariable UUID couponId,
                                                   @RequestBody @Valid CouponUpdateDTO dto,
                                                   @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.update(eventId, couponId, dto, authenticated));
    }

    @DeleteMapping("/{couponId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Apagar cupom",
            description = "Só cupom nunca usado; depois do uso, desative-o. Só o dono do evento ou um ADMIN.")
    @ApiResponse(responseCode = "204", description = "Cupom apagado.")
    @ProblemResponses({ProblemType.EVENT_ACCESS_DENIED, ProblemType.EVENT_NOT_FOUND, ProblemType.COUPON_NOT_FOUND,
            ProblemType.COUPON_ALREADY_USED})
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID couponId,
                                       @AuthenticationPrincipal User authenticated) {
        service.delete(eventId, couponId, authenticated);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    @Operation(summary = "Prever o desconto de um cupom",
            description = "Calcula o desconto para os itens informados, sem reservar nada. Qualquer conta logada. "
                    + "Falhas contam no limite de 10 tentativas a cada 15 minutos, compartilhado com `POST /orders`.")
    @ProblemResponses({ProblemType.TICKET_TYPE_NOT_FOUND, ProblemType.SALES_CLOSED, ProblemType.INVALID_COUPON,
            ProblemType.COUPON_BUYER_LIMIT_REACHED, ProblemType.TOO_MANY_COUPON_ATTEMPTS})
    public ResponseEntity<CouponPreviewDTO> preview(@PathVariable UUID eventId,
                                                    @RequestBody @Valid CouponPreviewRequestDTO dto,
                                                    @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.preview(eventId, dto, authenticated));
    }
}
