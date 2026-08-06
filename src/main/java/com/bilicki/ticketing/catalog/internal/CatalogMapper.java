package com.bilicki.ticketing.catalog.internal;

import com.bilicki.ticketing.catalog.web.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CatalogMapper {
    VenueResponse toVenueResponse(Venue venue);
    HallResponse toHallResponse(Hall hall);
    MovieResponse toMovieResponse(Movie movie);
    @Mapping(target = "hallId", source = "hall.id")
    @Mapping(target = "movieId", source = "movie.id")
    ShowtimeResponse toShowtimeResponse(Showtime showtime);
    HallSummaryResponse toHallSummaryResponse(Hall hall);
    VenueWithHallsResponse toVenueWithHallsResponse(Venue venue);
    SeatTypeResponse toSeatTypeResponse(SeatType seatType);
    @Mapping(target = "showtimeSeatId", source = "id")
    @Mapping(target = "row", source = "seat.rowLabel")
    @Mapping(target = "number", source = "seat.seatNumber")
    @Mapping(target = "type", source = "seat.seatType.name")
    ShowtimeSeatResponse toShowtimeSeatResponse(ShowtimeSeat showtimeSeat);
}
