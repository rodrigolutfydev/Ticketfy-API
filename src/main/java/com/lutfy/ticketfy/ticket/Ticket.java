package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.exception.InvalidTicketStateException;
import com.lutfy.ticketfy.order.Order;
import com.lutfy.ticketfy.order.OrderItem;
import com.lutfy.ticketfy.tickettype.TicketType;
import com.lutfy.ticketfy.user.User;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String code;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
    @Enumerated(EnumType.STRING)
    private TicketStatus status;
    private Instant usedAt;
    private int transferCount;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public Ticket(Order order, OrderItem orderItem, String code) {
        this.order = order;
        this.orderItem = orderItem;
        this.ticketType = orderItem.getTicketType();
        this.owner = order.getUser();
        this.code = code;
        this.status = TicketStatus.VALID;
    }

    public void cancel() {
        if (status != TicketStatus.VALID) {
            throw new InvalidTicketStateException("Only valid tickets can be cancelled");
        }
        this.status = TicketStatus.CANCELLED;
    }
}
