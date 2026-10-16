package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.OpenApiExamples;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = ApiTags.ORDERS)
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Criar pedido",
            description = "Reserva os ingressos por 15 minutos com status `PENDING`; pague com "
                    + "`POST /orders/{orderId}/payment`. Todos os itens precisam ser do mesmo evento. Pedido de "
                    + "valor zero (cupom de 100%) já nasce `PAID`, com os ingressos emitidos.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "pedido", summary = "Dois ingressos com cupom",
                    value = OpenApiExamples.ORDER_REQUEST)))
    @ApiResponse(responseCode = "201", description = "Pedido criado, ou o mesmo pedido de antes quando a "
            + "`Idempotency-Key` se repete.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = OrderDetailsDTO.class),
                    examples = @ExampleObject(name = "pedido", summary = "Pedido pendente",
                            value = OpenApiExamples.ORDER_RESPONSE)))
    @ProblemResponses({ProblemType.VALIDATION_FAILED, ProblemType.SESSION_EXPIRED, ProblemType.MIXED_EVENTS_ORDER,
            ProblemType.MAX_PER_ORDER_EXCEEDED, ProblemType.ORDER_NOT_FOUND, ProblemType.TICKET_TYPE_NOT_FOUND,
            ProblemType.INSUFFICIENT_STOCK, ProblemType.SALES_CLOSED, ProblemType.INVALID_EVENT_STATE,
            ProblemType.MAX_PER_BUYER_EXCEEDED, ProblemType.INVALID_COUPON, ProblemType.COUPON_BUYER_LIMIT_REACHED,
            ProblemType.TOO_MANY_COUPON_ATTEMPTS})
    public ResponseEntity<OrderDetailsDTO> create(
            @RequestBody @Valid OrderCreationDTO dto,
            @Parameter(description = "Opcional. Repetir a chave devolve o mesmo pedido em vez de criar outro "
                    + "(1 a 100 caracteres ASCII visíveis; recomendado um UUID por tentativa). Chave de outro "
                    + "usuário responde 404 `order-not-found`.")
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(min = 1, max = 100, message = "Idempotency-Key must have between 1 and 100 characters")
            @Pattern(regexp = "[\\x21-\\x7E]*", message = "Idempotency-Key must contain only visible ASCII characters")
            String idempotencyKey,
            @AuthenticationPrincipal User authenticated) {
        OrderDetailsDTO order;
        try {
            order = service.create(dto, idempotencyKey, authenticated);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) {
                throw ex;
            }
            order = service.findByIdempotencyKey(idempotencyKey, authenticated)
                    .orElseThrow(() -> ex);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver pedido", description = "Só pedidos da própria conta; os de outros respondem 404.")
    @ProblemResponses(ProblemType.ORDER_NOT_FOUND)
    public ResponseEntity<OrderDetailsDTO> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.findById(id, authenticated));
    }

    @GetMapping
    @Operation(summary = "Listar meus pedidos")
    public ResponseEntity<Page<OrderSummaryDTO>> listMyOrders(
            @AuthenticationPrincipal User authenticated,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(service.listMyOrders(authenticated, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar pedido pendente",
            description = "Libera a reserva e devolve o uso do cupom. Só pedidos `PENDING`; pago, use o reembolso.")
    @ProblemResponses({ProblemType.ORDER_NOT_FOUND, ProblemType.INVALID_ORDER_STATE})
    public ResponseEntity<OrderDetailsDTO> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.cancel(id, authenticated));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Pedir reembolso",
            description = "Reembolsa um pedido pago e cancela os ingressos, até 48 horas antes do início do evento. "
                    + "Recusado com ingresso usado ou transferido.")
    @ProblemResponses({ProblemType.ORDER_NOT_FOUND, ProblemType.INVALID_ORDER_STATE,
            ProblemType.INVALID_PAYMENT_STATE, ProblemType.ORDER_HAS_TRANSFERRED_TICKETS})
    public ResponseEntity<OrderDetailsDTO> refund(
            @PathVariable UUID id,
            @AuthenticationPrincipal User authenticated) {
        return ResponseEntity.ok(service.refund(id, authenticated));
    }
}
