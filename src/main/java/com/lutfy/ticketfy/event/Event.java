package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.user.User;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "venue_name")
    private String venueName;
    private String address;
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String state;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organizer_id")
    private User organizer;
    private boolean active;
    private boolean featured;

    @Enumerated(EnumType.STRING)
    private EventCategory category;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private Instant updatedAt;

    public Event(EventCreationDTO dto, User organizer) {
        this.name = dto.name();
        this.description = dto.description();
        this.imageUrl = blankToNull(dto.imageUrl());
        this.venueName = dto.venueName();
        this.address = dto.address();
        this.city = dto.city();
        this.state = dto.state();
        this.startsAt = dto.startsAt();
        this.endsAt = dto.endsAt();
        this.category = dto.category();
        this.organizer = organizer;
        this.active = true;
    }

    public void updateFrom(EventUpdateDTO dto) {
        if (dto.name() != null) this.name = dto.name();
        if (dto.description() != null) this.description = dto.description();
        if (dto.imageUrl() != null) this.imageUrl = blankToNull(dto.imageUrl());
        if (dto.venueName() != null) this.venueName = dto.venueName();
        if (dto.address() != null) this.address = dto.address();
        if (dto.city() != null) this.city = dto.city();
        if (dto.state() != null) this.state = dto.state();
        if (dto.startsAt() != null) this.startsAt = dto.startsAt();
        if (dto.endsAt() != null) this.endsAt = dto.endsAt();
        if (dto.category() != null) this.category = dto.category();
    }

    public void deactivate() {
        this.active = false;
    }

    public void changeFeatured(boolean featured) {
        this.featured = featured;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Local em uma linha: casa, endereço, cidade - UF (partes vazias ficam de fora). */
    public String fullLocation() {
        var cityAndState = Stream.of(city, state)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" - "));
        return Stream.of(venueName, address, cityAndState)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}