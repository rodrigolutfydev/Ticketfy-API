package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.order.OrderCreationDTO;
import com.lutfy.ticketfy.order.OrderItemRequestDTO;
import com.lutfy.ticketfy.order.OrderService;
import com.lutfy.ticketfy.payment.PaymentService;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

abstract class TicketTransferTestBase extends IntegrationTestBase {

    static final String PASSWORD = "senha-forte-123";

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderService orderService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    TicketTransferService transferService;

    @Autowired
    TicketService ticketService;

    @Autowired
    PasswordEncoder passwordEncoder;

    User organizer;
    UUID eventId;
    UUID ticketTypeId;

    @BeforeEach
    void setUpEvent() {
        organizer = user("ORGANIZER");
        eventId = insertEvent(organizer.getId());
        ticketTypeId = insertTicketType(eventId, 100);
    }

    User user(String role) {
        var id = insertUser(role);
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), id);
        return userRepository.findById(id).orElseThrow();
    }

    UUID paidOrder(User buyer, int quantity) {
        var dto = new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, quantity)));
        var orderId = orderService.create(dto, null, buyer).id();
        paymentService.paySimulated(orderId, buyer);
        return orderId;
    }

    UUID ticketOf(UUID orderId) {
        return jdbc.queryForObject("SELECT id FROM tickets WHERE order_id = ? ORDER BY created_at, id LIMIT 1",
                UUID.class, orderId);
    }

    String code(UUID ticketId) {
        return jdbc.queryForObject("SELECT code FROM tickets WHERE id = ?", String.class, ticketId);
    }

    UUID owner(UUID ticketId) {
        return jdbc.queryForObject("SELECT owner_id FROM tickets WHERE id = ?", UUID.class, ticketId);
    }

    String ticketStatus(UUID ticketId) {
        return jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticketId);
    }

    String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    int transfers(UUID ticketId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_transfers WHERE ticket_id = ?", Integer.class, ticketId);
    }

    TicketTransferRequestDTO to(User recipient) {
        return new TicketTransferRequestDTO(recipient.getEmail(), PASSWORD);
    }
}
