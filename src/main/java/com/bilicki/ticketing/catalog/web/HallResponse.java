package com.bilicki.ticketing.catalog.web;

import java.util.UUID;

public record HallResponse (
      UUID id,
      VenueResponse venue,
      String name
) {}
