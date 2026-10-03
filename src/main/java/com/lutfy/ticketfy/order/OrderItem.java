package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.tickettype.TicketType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;
    private BigDecimal unitPrice;
    private Integer quantity;
    @CreationTimestamp
    private Instant createdAt;

    public OrderItem(Order order, TicketType ticketType, Integer quantity) {
        this.order = order;
        this.ticketType = ticketType;
        this.quantity = quantity;
        this.unitPrice = ticketType.getPrice();
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
