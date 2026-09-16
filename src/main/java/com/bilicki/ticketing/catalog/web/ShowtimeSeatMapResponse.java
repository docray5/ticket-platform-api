package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.ShowtimeSeatResponse;

import java.util.List;
import java.util.UUID;

public record ShowtimeSeatMapResponse (
    UUID showtimeId,
    HallSummaryResponse hall,
    List<ShowtimeSeatResponse> showtimeSeats
) {}
