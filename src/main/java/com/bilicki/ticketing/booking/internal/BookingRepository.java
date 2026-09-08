package com.bilicki.ticketing.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.bookingSeats WHERE b.id = :bookingId")
    Optional<Booking> findByIdWithSeats(@Param("bookingId") UUID bookingId);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingSeats WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    List<Booking> findAllByUserIdWithSeats(@Param("userId") UUID userId);
}
