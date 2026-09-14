package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private Integer quantityTotal;
    private Integer quantitySold;
    private Integer maxPerOrder;
    private Boolean active;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Integer availableQuantity() {
        return quantityTotal - quantitySold;
    }
}
