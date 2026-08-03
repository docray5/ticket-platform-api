package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record MovieResponse(
        UUID id,
        String title,
        String description,
        Integer durationMinutes
) {}
