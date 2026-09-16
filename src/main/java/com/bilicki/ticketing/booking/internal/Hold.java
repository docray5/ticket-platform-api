package com.bilicki.ticketing.booking.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Table(name = "holds")
@Getter
@Entity
public class Hold {
    public enum HoldStatus {
        ACTIVE, CONFIRMED, EXPIRED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "showtime_id", nullable = false)
    private UUID showtimeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Setter
    private HoldStatus status = HoldStatus.ACTIVE;

    @Column(nullable = false, name = "total_price")
    private BigDecimal totalPrice;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "hold", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HoldSeat> seats = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public Hold(UUID showtimeId, UUID userId, BigDecimal totalPrice, Instant expiresAt) {
        this.showtimeId = showtimeId;
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.expiresAt = expiresAt;
    }
}
