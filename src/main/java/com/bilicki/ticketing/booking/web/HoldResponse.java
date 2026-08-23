package com.bilicki.ticketing.booking.web;

import com.bilicki.ticketing.booking.internal.Hold;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldResponse(
    UUID holdId,
    UUID showtimeId,
    Hold.HoldStatus holdStatus,
    BigDecimal totalPrice,
    Instant createdAt,
    Instant expiresAt,
    List<HoldSeatResponse> holdSeats
) { }
