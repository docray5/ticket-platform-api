package com.bilicki.ticketing.catalog.web;

import java.math.BigDecimal;
import java.util.UUID;

public record ShowtimeSeatResponse (
        UUID showtimeSeatId,
        String row,
        Short number,
        String type,
        BigDecimal price,
        String status
) {}
