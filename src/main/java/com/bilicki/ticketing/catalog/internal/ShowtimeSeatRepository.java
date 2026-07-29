package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, UUID> {

}
