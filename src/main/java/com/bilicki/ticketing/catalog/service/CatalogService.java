package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.catalog.web.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CatalogService {
    private HallRepository hallRepository;
    private CatalogMapper catalogMapper;
    private VenueRepository venueRepository;
    private MovieRepository movieRepository;

    public VenueResponse createVenue(VenueRequest request) {
        if (venueRepository.existsVenueByName(request.name()) || venueRepository.existsVenueByAddress(request.address()))
            throw new VenueConflictException(request.name(), request.address());

        Venue venue = new Venue(request.name(), request.address());

        Venue savedVenue = venueRepository.save(venue);

        return catalogMapper.toVenueResponse(savedVenue);
    }

    public List<VenueResponse> getAllVenues() {
        return venueRepository.findAll().stream().map(catalogMapper::toVenueResponse).toList();
    }

    public HallResponse createHall(HallRequest request) {
        if (hallRepository.existsByVenueIdAndName(request.venueId(), request.name()))
            throw new HallConflictException(request.name());

        Venue venue = venueRepository.findById(request.venueId()).orElseThrow(VenueNotFoundException::new);

        Hall hall = new Hall(venue, request.name());

        Hall savedHall = hallRepository.save(hall);

        return catalogMapper.toHallResponse(savedHall);
    }

    public List<HallResponse> getAllHalls() {
        return hallRepository.findAll().stream().map(catalogMapper::toHallResponse).toList();
    }

    public MovieResponse createMovie(MovieRequest request) {
        if (movieRepository.existsMovieByTitle(request.title()))
            throw new MovieConflictException(request.title());

        Movie movie = new Movie(request.title(), request.description(), request.durationMinutes());

        Movie savedMovie = movieRepository.save(movie);

        return catalogMapper.toMovieResponse(savedMovie);
    }

    public List<MovieResponse> getAllMovies() {
        return movieRepository.findAll().stream().map(catalogMapper::toMovieResponse).toList();
    }
}
