package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TicketType {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id")
    private Event event;
    private String name;
    private String description;
    private BigDecimal price;
    @Column(updatable = false)
    private Integer quantityTotal;
    @Column(updatable = false)
    private Integer quantitySold;
    private Integer maxPerOrder;
    private Boolean active;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public TicketType(TicketTypeCreationDTO dto, Event event) {
        this.event = event;
        this.name = dto.name();
        this.description = dto.description();
        this.price = dto.price();
        this.quantityTotal = dto.quantityTotal();
        this.maxPerOrder = dto.maxPerOrder();
        this.quantitySold = 0;
        this.active = true;
    }

    public void updateFrom(TicketTypeUpdateDTO dto) {
        if (dto.name() != null) this.name = dto.name();
        if (dto.description() != null) this.description = dto.description().isBlank() ? null : dto.description();
        if (dto.price() != null) this.price = dto.price();
        if (dto.maxPerOrder() != null) this.maxPerOrder = dto.maxPerOrder();
    }

    public Integer availableQuantity() {
        return quantityTotal - quantitySold;
    }
}
