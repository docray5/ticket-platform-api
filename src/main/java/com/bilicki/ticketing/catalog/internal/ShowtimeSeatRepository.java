package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, UUID> {
    @Query("""
        SELECT ss FROM ShowtimeSeat ss JOIN FETCH ss.seat s JOIN FETCH s.seatType WHERE ss.showtime.id = :showtimeId
    """)
    List<ShowtimeSeat> findAllByShowtimeId(@Param("showtimeId") UUID showtimeId);

    boolean existsByShowtimeId(UUID showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ss FROM ShowtimeSeat ss WHERE ss.showtime.id = :showtimeId AND ss.id in :showtimeSeatIds ORDER BY ss.id
    """)
    List<ShowtimeSeat> findAndLockAllByShowtimeIdAndInSeatIds(@Param("showtimeId") UUID showtimeId, @Param("showtimeSeatIds") List<UUID> showtimeSeatIds);

    @Query("SELECT ss FROM ShowtimeSeat ss JOIN FETCH ss.seat s JOIN FETCH s.seatType WHERE ss.id IN :ids")
    List<ShowtimeSeat> findAllWithSeatDetailsByIdIn(@Param("ids") List<UUID> ids);
}
