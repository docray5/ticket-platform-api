package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "showtime_seats")
@Getter
@Entity
public class ShowtimeSeat {
    public enum SeatStatus {
        AVAILABLE, HELD, BOOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "showtime_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Showtime showtime;

    @JoinColumn(name = "seat_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Seat seat;

    @Column(nullable = false)
    private BigDecimal price;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public ShowtimeSeat(Showtime showtime, Seat seat, BigDecimal price) {
        this.showtime = showtime;
        this.seat = seat;
        this.price = price;
    }
}
