package com.lutfy.ticketfy.payment;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders/{orderId}/payment")
@Tag(name = ApiTags.ORDERS)
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Pagar pedido",
            description = "Pagamento simulado: aprova na hora, marca o pedido como `PAID` e emite os ingressos.")
    @ApiResponse(responseCode = "201", description = "Pagamento aprovado.")
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.ORDER_NOT_FOUND, ProblemType.INVALID_EVENT_STATE,
            ProblemType.INVALID_ORDER_STATE, ProblemType.INVALID_PAYMENT_STATE})
    public ResponseEntity<PaymentDetailsDTO> pay(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User authenticated) {
        var payment = service.paySimulated(orderId, authenticated);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
}