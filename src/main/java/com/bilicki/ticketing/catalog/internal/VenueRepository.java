package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    boolean existsVenueByName(String name);
    boolean existsVenueByAddress(String address);
}
