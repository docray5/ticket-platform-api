package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record HallResponse (
      UUID venueId,
      String name
) {}
