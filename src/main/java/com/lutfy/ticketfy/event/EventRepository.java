package com.lutfy.ticketfy.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndActiveTrue(UUID id);

    Page<Event> findByActiveTrue(Pageable pageable);

    Page<Event> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Event> findByActiveTrueAndCityIgnoreCase(String city, Pageable pageable);

    Page<Event> findByActiveTrueAndNameContainingIgnoreCaseAndCityIgnoreCase(
            String name, String city, Pageable pageable);
}
