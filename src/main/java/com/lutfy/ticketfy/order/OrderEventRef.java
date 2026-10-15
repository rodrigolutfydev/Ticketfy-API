package com.lutfy.ticketfy.order;

import java.time.Instant;
import java.util.UUID;

/** Evento de um pedido (todo pedido tem itens de um único evento). */
public record OrderEventRef(UUID orderId, UUID eventId, String eventName, Instant eventStartsAt) {
}
