package com.bilicki.ticketing.catalog.internal;

import com.bilicki.ticketing.catalog.web.HallResponse;
import com.bilicki.ticketing.catalog.web.MovieResponse;
import com.bilicki.ticketing.catalog.web.ShowtimeResponse;
import com.bilicki.ticketing.catalog.web.VenueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CatalogMapper {
    VenueResponse toVenueResponse(Venue venue);
    @Mapping(target = "venueId", source = "venue.id")
    HallResponse toHallResponse(Hall hall);
    MovieResponse toMovieResponse(Movie movie);
    @Mapping(target = "hallId", source = "hall.id")
    @Mapping(target = "movieId", source = "movie.id")
    ShowtimeResponse toShowtimeResponse(Showtime showtime);
}
