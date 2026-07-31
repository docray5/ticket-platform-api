package com.bilicki.ticketing.catalog.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShowtimeResponse (
        UUID id,
        UUID movieId,
        UUID hallId,
        Instant startTime,
        Instant endTime,
        BigDecimal basePrice
) {}
