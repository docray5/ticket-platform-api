package com.bilicki.ticketing.catalog.web;

import java.util.List;
import java.util.UUID;

public record VenueWithHallsResponse(
        UUID id,
        String name,
        String address,
        List<HallSummaryResponse> halls
) { }
