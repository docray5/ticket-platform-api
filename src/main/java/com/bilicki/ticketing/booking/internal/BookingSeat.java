package com.bilicki.ticketing.booking.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "booking_seats")
@Getter
@Entity
public class BookingSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "booking_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Column(name = "showtime_seat_id", nullable = false)
    private UUID showtimeSeatId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public BookingSeat(Booking booking, UUID showtimeSeatId) {
        this.booking = booking;
        this.showtimeSeatId = showtimeSeatId;
    }
}
