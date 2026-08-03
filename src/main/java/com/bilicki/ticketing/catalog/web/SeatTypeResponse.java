package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record SeatTypeResponse(
        UUID id,
        String name
) {}
