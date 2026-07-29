package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Table(name = "seats")
@Entity
@NoArgsConstructor
@Getter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "hall_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Hall hall;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Short seatNumber;

    @JoinColumn(name = "seat_type_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private SeatType seatType;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();
}
