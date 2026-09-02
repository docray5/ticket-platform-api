package com.bilicki.ticketing.booking.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface HoldRepository extends JpaRepository<Hold, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Hold> findAndLockById(UUID holdId);
}
