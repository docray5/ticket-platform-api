package com.bilicki.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "venues")
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    public Venue(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
