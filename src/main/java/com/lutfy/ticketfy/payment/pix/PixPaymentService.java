package com.lutfy.ticketfy.payment.pix;

import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserLocks;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Usuário não autenticado"
            );
        }

        // 2. Verificar se o usuário está ativo
        // Requer uma transação ativa para executar FOR KEY SHARE.
        userLocks.requireActive(authenticated.getId());

        // 3. Buscar o pedido
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido não encontrado"
                ));

        // 4. Verificar se o pedido pertence ao usuário
        // Retornar 404 evita revelar pedidos de outros usuários.
        if (!order.getUser().getId().equals(authenticated.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Pedido não encontrado"
            );
        }

        // 5. Verificar status do pedido
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido não está pendente. Status atual: "
                            + order.getStatus()
            );
        }

        // 6. Verificar se o evento foi cancelado
        if (order.belongsToCancelledEvent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Não é possível pagar um pedido de evento cancelado"
            );
        }

        // 7. Verificar expiração da reserva
        if (order.isExpired(clock.instant())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido expirado. Crie uma nova reserva"
            );
        }

        // 8. Verificar se o pedido é gratuito
        if (order.isFree()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedidos gratuitos não precisam de pagamento Pix"
            );
        }

        // 9. Converter valor de reais para centavos
        long amountCents = order.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        if (amountCents <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O valor do pedido deve ser maior que zero"
            );
        }

        // 10. Criar ou recuperar pagamento no microsserviço Go
        // A operação no Go deve ser idempotente por orderId.
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

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "O serviço de pagamentos retornou dados inconsistentes"
            );
        }

        // 12. Solicitar geração do Pix ao microsserviço Go
        var pix = goPaymentClient.createPix(
                payment.id()
        );

        // 13. Validar a resposta do Pix
        if (pix == null
                || pix.paymentId() == null
                || pix.paymentId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "O serviço de pagamentos retornou um Pix inválido"
            );
        }

        // 14. Retornar dados do Pix
        // O pedido continua PENDING até a confirmação autenticada.
        return new PixPaymentResponse(
                order.getId(),
                payment.id(),
                pix.pixCopyPaste(),
                pix.pixQrCode(),
                pix.status()
        );
    }
}