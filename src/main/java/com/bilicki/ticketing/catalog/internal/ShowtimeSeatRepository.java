package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, UUID> {
    @Query("""
        SELECT ss FROM ShowtimeSeat ss JOIN FETCH ss.seat s JOIN FETCH s.seatType WHERE ss.showtime.id = :showtimeId
    """)
    List<ShowtimeSeat> findAllByShowtimeId(@Param("showtimeId") UUID showtimeId);

    boolean existsByShowtimeId(UUID showtimeId);
}
