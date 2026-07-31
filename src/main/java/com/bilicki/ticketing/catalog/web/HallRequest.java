package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotBlank;

public record HallRequest(
        @NotBlank(message = "Name is required")
        String name
) {
}
