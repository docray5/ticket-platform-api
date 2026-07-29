package com.bilicki.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ShowtimeRepository extends JpaRepository<Showtime, UUID> {
    @Query("""
                SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
                FROM Showtime s
                WHERE s.hall.id = :hallId
                    AND s.startTime < :endTime
                    AND s.endTime > :startTime
            """)
    boolean existsOverlappingShowtime(
            @Param("hallId") UUID hallId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
