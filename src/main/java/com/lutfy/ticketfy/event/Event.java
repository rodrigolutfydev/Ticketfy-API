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

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "venue_name")
    private String venueName;
    private String address;
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String state;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organizer_id")
    private User organizer;
    private boolean active;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Event(EventCreationDTO dto, User organizer) {
        this.name = dto.name();
        this.description = dto.description();
        this.venueName = dto.venueName();
        this.address = dto.address();
        this.city = dto.city();
        this.state = dto.state();
        this.startsAt = dto.startsAt();
        this.endsAt = dto.endsAt();
        this.organizer = organizer;
        this.active = true;
    }

    public void updateFrom(EventUpdateDTO dto) {
        if (dto.name() != null) this.name = dto.name();
        if (dto.description() != null) this.description = dto.description();
        if (dto.venueName() != null) this.venueName = dto.venueName();
        if (dto.address() != null) this.address = dto.address();
        if (dto.city() != null) this.city = dto.city();
        if (dto.state() != null) this.state = dto.state();
        if (dto.startsAt() != null) this.startsAt = dto.startsAt();
        if (dto.endsAt() != null) this.endsAt = dto.endsAt();
    }

    public void deactivate() {
        this.active = false;
    }
}