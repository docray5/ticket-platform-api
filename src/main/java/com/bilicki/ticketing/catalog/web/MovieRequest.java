package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MovieRequest(
        @NotBlank(message = "Title is required")
        String title,
        @NotBlank(message = "Description is required")
        String description,
        @NotNull(message = "Duration is required")
        @Positive(message = "Duration must be greater than zero")
        Integer durationMinutes
) {}
