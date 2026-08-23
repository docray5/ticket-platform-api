package com.bilicki.ticketing.booking.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "hold_seats")
@Getter
@Entity
public class HoldSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "hold_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Hold hold;

    @Column(name = "showtime_seat_id", nullable = false)
    private UUID showtimeSeatId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public HoldSeat(Hold hold, UUID showtimeSeatId) {
        this.hold = hold;
        this.showtimeSeatId = showtimeSeatId;
    }
}
