package com.bilicki.ticketing.catalog.internal;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Table(name = "seat_types")
@Entity
@NoArgsConstructor
@Getter
public class SeatType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "price_multiplier", nullable = false)
    private BigDecimal priceMultiplier = BigDecimal.ONE;

    public SeatType(String name, BigDecimal priceMultiplier) {
        this.name = name;
        this.priceMultiplier = priceMultiplier;
    }
}
