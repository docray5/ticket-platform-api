package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record HallRequest(
        @NotNull(message = "Venue ID is required")
        UUID venueId,
        @NotBlank(message = "Name is required")
        String name
) {
}
