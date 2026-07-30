package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record VenueResponse (
        UUID id,
        String name,
        String address
) {}
