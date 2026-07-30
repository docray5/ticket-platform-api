package com.bilicki.ticketing.catalog.web;

import jakarta.validation.constraints.NotBlank;

public record VenueRequest (
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Address is required")
        String address
) {}
