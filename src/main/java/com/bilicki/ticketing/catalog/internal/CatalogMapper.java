package com.bilicki.ticketing.catalog.internal;

import com.bilicki.ticketing.catalog.web.VenueResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CatalogMapper {
    VenueResponse toVenueResponse(Venue venue);
}
