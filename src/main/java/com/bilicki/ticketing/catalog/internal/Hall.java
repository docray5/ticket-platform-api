package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "halls")
public class Hall {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "venue_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();
}
