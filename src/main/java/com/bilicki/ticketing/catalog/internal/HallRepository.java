package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface HallRepository extends JpaRepository<Hall, UUID> {
    boolean existsByVenueIdAndName(UUID venueId, String name);
    @Query("SELECT h FROM Hall h JOIN FETCH h.venue")
    List<Hall> findAllHallsWithVenues();
}
