package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SeatGenerationRequest(
        @NotNull(message = "Number of rows is required")
        @Min(value = 1, message = "Number of rows must be positive")
        @Max(value = 26, message = "Maximum supported rows is 26 (A-Z)")
        Integer rowCount,
        @NotNull(message = "Number of seats per row is required")
        @Min(value = 1, message = "Number of seats per row must be positive")
        Integer seatsPerRow,
        @NotNull(message = "Seat type is required")
        UUID seatTypeId
) {
}
