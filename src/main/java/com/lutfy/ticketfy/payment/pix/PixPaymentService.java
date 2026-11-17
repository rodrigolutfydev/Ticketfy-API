package com.lutfy.ticketfy.payment.pix;

import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserLocks;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

@Service
public class PixPaymentService {

    private final OrderRepository orderRepository;
    private final GoPaymentClient goPaymentClient;
    private final UserLocks userLocks;
    private final Clock clock;

    public PixPaymentService(
            OrderRepository orderRepository,
            GoPaymentClient goPaymentClient,
            UserLocks userLocks,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.goPaymentClient = goPaymentClient;
        this.userLocks = userLocks;
        this.clock = clock;
    }

    @Transactional
    public PixPaymentResponse createPix(
            UUID orderId,
            User authenticated
    ) {

        // 1. Verificar autenticação
        if (authenticated == null || authenticated.getId() == null) {
            throw new IllegalArgumentException(
                    "Usuário não autenticado"
            );
        }

        // 2. Verificar se o usuário está ativo
        // Requer uma transação ativa para executar FOR KEY SHARE.
        userLocks.requireActive(authenticated.getId());

        // 3. Buscar o pedido
        var order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Pedido não encontrado"
                        )
                );

        // 4. Verificar se o pedido pertence ao usuário
        if (!order.getUser().getId().equals(authenticated.getId())) {
            throw new IllegalArgumentException(
                    "Pedido não encontrado"
            );
        }

        // 5. Verificar status do pedido
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Pedido não está pendente"
            );
        }

        // 6. Verificar se o evento foi cancelado
        if (order.belongsToCancelledEvent()) {
            throw new IllegalStateException(
                    "Evento cancelado"
            );
        }

        // 7. Verificar expiração
        if (order.isExpired(clock.instant())) {
            throw new IllegalStateException(
                    "Pedido expirado"
            );
        }

        // 8. Verificar se o pedido é gratuito
        if (order.isFree()) {
            throw new IllegalStateException(
                    "Pedidos gratuitos não precisam de Pix"
            );
        }

        // 9. Converter valor de reais para centavos
        long amountCents = order.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        if (amountCents <= 0) {
            throw new IllegalStateException(
                    "Valor inválido para pagamento"
            );
        }

        // 10. Criar ou recuperar pagamento no serviço Go
        var payment = goPaymentClient.createPayment(
                order.getId(),
                amountCents
        );

        // 11. Validar a resposta do Go
        if (payment == null
                || payment.id() == null
                || !orderId.toString().equals(payment.orderId())
                || payment.amountCents() != amountCents
                || !"BRL".equals(payment.currency())
                || !"PENDING".equals(payment.status())) {

            throw new IllegalStateException(
                    "Pagamento retornado pelo Go não corresponde ao pedido"
            );
        }

        // 12. Solicitar geração do Pix ao serviço Go
        var pix = goPaymentClient.createPix(
                payment.id()
        );

        // 13. Validar a resposta do Pix
        if (pix == null
                || pix.paymentId() == null
                || pix.paymentId().isBlank()) {

            throw new IllegalStateException(
                    "Resposta inválida ao gerar Pix"
            );
        }

        // 14. Retornar os dados do pagamento
        return new PixPaymentResponse(
                order.getId(),
                payment.id(),
                pix.pixCopyPaste(),
                pix.pixQrCode(),
                pix.status()
        );
    }
}