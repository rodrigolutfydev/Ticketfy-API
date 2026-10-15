package com.lutfy.ticketfy.notification;

import java.util.UUID;

/** Pedido ficou PAID (pagamento aprovado ou pedido gratuito). */
public record OrderConfirmedEvent(UUID orderId) {
}
