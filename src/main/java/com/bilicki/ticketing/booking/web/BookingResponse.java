package com.bilicki.ticketing.booking.web;

import com.bilicki.ticketing.catalog.ShowtimeSeatResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
    UUID bookingId,
    UUID showtimeId,
    BigDecimal totalPrice,
    String status,
    List<ShowtimeSeatResponse> seats
) { }
