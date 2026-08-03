package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "showtimes")
@Getter
@Entity
public class Showtime {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "movie_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Movie movie;

    @JoinColumn(name = "hall_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Hall hall;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public Showtime(Movie movie, Hall hall, Instant startTime, Instant endTime, BigDecimal basePrice) {
        this.basePrice = basePrice;
        this.endTime = endTime;
        this.startTime = startTime;
        this.hall = hall;
        this.movie = movie;
    }
}
