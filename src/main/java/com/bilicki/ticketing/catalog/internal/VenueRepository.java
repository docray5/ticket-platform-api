package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    boolean existsVenueByName(String name);
    boolean existsVenueByAddress(String address);
    @Query("SELECT v FROM Venue v LEFT JOIN FETCH v.halls")
    List<Venue> findAllVenuesWithHalls();
}
