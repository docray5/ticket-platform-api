package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {
    private CatalogService catalogService;

    @PostMapping(path = "/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueRequest request) {
        return catalogService.createVenue(request);
    }

    @GetMapping(path = "/venues")
    @ResponseStatus(HttpStatus.OK)
    public List<VenueResponse> getAllVenues() {
        return catalogService.getAllVenues();
    }

    @PostMapping(path = "/venues/{venueId}/halls")
    @ResponseStatus(HttpStatus.CREATED)
    public HallResponse createHall(@PathVariable UUID venueId, @Valid @RequestBody HallRequest request) {
        return catalogService.createHall(venueId, request);
    }

    @GetMapping(path = "/halls")
    @ResponseStatus(HttpStatus.OK)
    public List<HallResponse> getAllHalls() {
        return catalogService.getAllHalls();
    }

    @GetMapping(path = "/venues/detailed")
    @ResponseStatus(HttpStatus.OK)
    public List<VenueWithHallsResponse> getAllVenuesWithHalls() {
        return catalogService.getAllVenuesWithHalls();
    }

    @PostMapping(path = "/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return catalogService.createMovie(request);
    }

    @GetMapping(path = "/movies")
    @ResponseStatus(HttpStatus.OK)
    public List<MovieResponse> getAllMovies() {
        return catalogService.getAllMovies();
    }

    @PostMapping(path = "/halls/{hallId}/seats:bulk-generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Integer bulkGenerateSeats(@PathVariable UUID hallId, @Valid @RequestBody SeatGenerationRequest request) {
        return catalogService.bulkGenerateSeats(hallId, request);
    }

    @PostMapping(path = "/showtimes")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowtimeResponse createShowtime(@Valid @RequestBody ShowtimeRequest request) {
        return catalogService.createShowtime(request);
    }

    @GetMapping(path = "/seat-types")
    @ResponseStatus(HttpStatus.OK)
    public List<SeatTypeResponse> getAllSeatTypes() {
        return catalogService.getAllSeatTypes();
    }
}
