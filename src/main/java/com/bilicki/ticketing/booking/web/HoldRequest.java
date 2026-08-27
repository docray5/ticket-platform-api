package com.bilicki.ticketing.booking.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HoldRequest(
        @NotEmpty(message = "At least one seat must be selected")
        List<@NotNull UUID> showtimeSeatIds
) {
}
