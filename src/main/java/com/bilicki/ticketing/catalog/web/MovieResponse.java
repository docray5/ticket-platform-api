package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotBlank;

public record MovieResponse(
        String title,
        String description,
        Integer durationMinutes
) {}
