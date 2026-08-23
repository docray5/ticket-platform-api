package com.bilicki.ticketing.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HoldRepository extends JpaRepository<Hold, UUID> {
}
