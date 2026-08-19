package com.bilicki.ticketing.booking.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "bookings")
@Getter
@Entity
public class Booking {
    public enum BookingStatus {
        CONFIRMED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "showtime_id", nullable = false)
    private UUID showtimeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JoinColumn(name = "hold_id")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Hold hold;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(nullable = false, name = "total_price")
    private BigDecimal totalPrice;

    @Column(name = "confirmed_at", updatable = false, nullable = false)
    private Instant confirmedAt = Instant.now();

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();
}
