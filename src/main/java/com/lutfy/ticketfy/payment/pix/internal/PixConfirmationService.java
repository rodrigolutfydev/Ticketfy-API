package com.lutfy.ticketfy.payment.pix.internal;

import com.lutfy.ticketfy.notification.OrderConfirmedEvent;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.payment.Payment;
import com.lutfy.ticketfy.payment.PaymentMethod;
import com.lutfy.ticketfy.payment.PaymentRepository;
import com.lutfy.ticketfy.payment.PaymentStatus;
import com.lutfy.ticketfy.payout.ledger.LedgerService;
import com.lutfy.ticketfy.ticket.TicketService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.Clock;

@Service
public class PixConfirmationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final TicketService ticketService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PixConfirmationService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            LedgerService ledgerService,
            TicketService ticketService,
            ApplicationEventPublisher events,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerService = ledgerService;
        this.ticketService = ticketService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PixConfirmationResponse confirm(
            PixConfirmationRequest request
    ) {

        // 1. Busca o pedido com bloqueio transacional
        var order = orderRepository
                .findForUpdateById(request.orderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido não encontrado"
                ));

        String providerReference = request.paymentId().toString();

        // 2. Valida o valor do pagamento
        long expectedAmount = order.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        if (expectedAmount <= 0
                || expectedAmount != request.amountCents()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Valor do pagamento não corresponde ao pedido"
            );
        }

        // 3. Verifica se o pedido já possui pagamento aprovado
        var approvedPayment = paymentRepository
                .findByOrderIdAndStatus(
                        order.getId(),
                        PaymentStatus.APPROVED
                );

        if (approvedPayment.isPresent()) {

            var existing = approvedPayment.get();

            // Confirmação repetida do mesmo Pix
            if (existing.getMethod() == PaymentMethod.PIX
                    && providerReference.equals(
                            existing.getProviderReference()
                    )
                    && order.getStatus() == OrderStatus.PAID) {

                return new PixConfirmationResponse(
                        request.paymentId(),
                        order.getId(),
                        "PAID",
                        true
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido já possui outro pagamento aprovado"
            );
        }

        // 4. Verifica o status do pedido
        if (order.getStatus() != OrderStatus.PENDING) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido não está pendente"
            );
        }

        // 5. Verifica se o pedido expirou
        if (order.isExpired(clock.instant())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido expirado: pagamento requer conciliação"
            );
        }

        // 6. Verifica se o evento foi cancelado
        if (order.belongsToCancelledEvent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Evento cancelado"
            );
        }

        // 7. Registra o pagamento Pix
        Payment payment = new Payment(
                order,
                PaymentMethod.PIX
        );

        payment.setProviderReference(providerReference);
        payment.approve();

        // 8. Atualiza o pedido para PAID
        order.markAsPaid();

        // 9. Salva o pagamento
        paymentRepository.saveAndFlush(payment);

        // 10. Registra a receita
        ledgerService.recordSale(order);

        // 11. Emite os ingressos
        ticketService.issueForOrder(order);

        // 12. Publica o evento de confirmação
        events.publishEvent(
                new OrderConfirmedEvent(order.getId())
        );

        return new PixConfirmationResponse(
                request.paymentId(),
                order.getId(),
                "PAID",
                false
        );
    }
}
