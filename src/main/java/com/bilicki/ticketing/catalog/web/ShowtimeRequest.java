package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShowtimeRequest (
        @NotNull(message = "Movie ID is required")
        UUID movieId,
        @NotNull(message = "Hall ID is required")
        UUID hallId,
        @NotNull(message = "Start time is required")
        Instant startTime,
        @NotNull(message = "Base price is required")
        @Positive
        BigDecimal basePrice
) {}
