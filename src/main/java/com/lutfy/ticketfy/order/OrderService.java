package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.infra.exception.*;
import com.lutfy.ticketfy.tickettype.TicketTypeRepository;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final long reservationMinutes;

    public OrderService(OrderRepository orderRepository,
                        TicketTypeRepository ticketTypeRepository,
                        @Value("${ticketfy.order.reservation-minutes}") long reservationMinutes) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.reservationMinutes = reservationMinutes;
    }


    @Transactional
    public OrderDetailsDTO create(OrderCreationDTO dto, String idempotencyKey, User authenticated) {
        if (idempotencyKey != null) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                checkOwnership(existing.get(), authenticated);
                return new OrderDetailsDTO(existing.get());
            }
        }
        var expiresAt = LocalDateTime.now().plusMinutes(reservationMinutes);
        var order = new Order(authenticated, expiresAt, idempotencyKey);
        for (var itemRequest : dto.items()) {
            var ticketType = ticketTypeRepository.findByIdAndActiveTrue(itemRequest.ticketTypeId())
                    .orElseThrow(() -> new TicketTypeNotFoundException("Ticket type not found"));
            if (ticketType.getMaxPerOrder() != null
                    && itemRequest.quantity() > ticketType.getMaxPerOrder()) {
                throw new MaxPerOrderExceededException(
                        "Maximum " + ticketType.getMaxPerOrder() + " tickets per order for this ticket type");
            }
            int affectedRows = ticketTypeRepository.reserveStock(ticketType.getId(), itemRequest.quantity());
            if (affectedRows == 0) {
                throw new InsufficientStockException("Not enough tickets available for this ticket type");
            }
            var item = new OrderItem(order, ticketType, itemRequest.quantity());
            order.addItem(item);
        }
        var saved = orderRepository.saveAndFlush(order);
        return new OrderDetailsDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrderDetailsDTO findById(UUID id, User authenticated) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        checkOwnership(order, authenticated);
        return new OrderDetailsDTO(order);
    }

    @Transactional(readOnly = true)
    public Optional<OrderDetailsDTO> findByIdempotencyKey(String idempotencyKey, User authenticated) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(order -> {
                    checkOwnership(order, authenticated);
                    return new OrderDetailsDTO(order);
                });
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> listMyOrders(User authenticated, Pageable pageable) {
        return orderRepository.findByUserId(authenticated.getId(), pageable)
                .map(OrderSummaryDTO::new);
    }

    @Transactional
    public OrderDetailsDTO cancel(UUID id, User authenticated) {
        var order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        checkOwnership(order, authenticated);
        order.cancel();
        for (var item : order.getItems()) {
            ticketTypeRepository.releaseStock(item.getTicketType().getId(), item.getQuantity());
        }
        return new OrderDetailsDTO(order);
    }

    private void checkOwnership(Order order, User authenticated) {
        if (authenticated.getRole() == Role.ADMIN) return;
        if (!order.getUser().equals(authenticated)) {
            throw new OrderAccessDeniedException("You do not own this order");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findOverdueOrderIds() {
        return orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING, LocalDateTime.now())
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Transactional
    public boolean expireOrder(UUID id) {
        var order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        order.expire();
        for (var item : order.getItems()) {
            ticketTypeRepository.releaseStock(item.getTicketType().getId(), item.getQuantity());
        }
        return true;
    }
}
