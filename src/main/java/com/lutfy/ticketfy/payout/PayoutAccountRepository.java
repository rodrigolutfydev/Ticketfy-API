package com.lutfy.ticketfy.payout;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PayoutAccount a WHERE a.organizerId = :organizerId")
    Optional<PayoutAccount> findForUpdate(@Param("organizerId") UUID organizerId);
}
