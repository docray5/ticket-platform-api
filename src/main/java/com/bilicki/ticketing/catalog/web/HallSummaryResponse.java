package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record HallSummaryResponse (
        UUID id,
        String name
) {}
