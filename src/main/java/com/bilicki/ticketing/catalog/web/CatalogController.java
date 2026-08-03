package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class CatalogController {
    private CatalogService catalogService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/admin/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueRequest request) {
        return catalogService.createVenue(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/admin/venues")
    @ResponseStatus(HttpStatus.OK)
    public List<VenueResponse> getAllVenues() {
        return catalogService.getAllVenues();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/admin/venues/{venueId}/halls")
    @ResponseStatus(HttpStatus.CREATED)
    public HallResponse createHall(@PathVariable UUID venueId, @Valid @RequestBody HallRequest request) {
        return catalogService.createHall(venueId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/admin/halls")
    @ResponseStatus(HttpStatus.OK)
    public List<HallResponse> getAllHalls() {
        return catalogService.getAllHalls();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/admin/venues/detailed")
    @ResponseStatus(HttpStatus.OK)
    public List<VenueWithHallsResponse> getAllVenuesWithHalls() {
        return catalogService.getAllVenuesWithHalls();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/admin/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return catalogService.createMovie(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/admin/movies")
    @ResponseStatus(HttpStatus.OK)
    public List<MovieResponse> getAllMovies() {
        return catalogService.getAllMovies();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/admin/halls/{hallId}/seats:bulk-generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Integer bulkGenerateSeats(@PathVariable UUID hallId, @Valid @RequestBody SeatGenerationRequest request) {
        return catalogService.bulkGenerateSeats(hallId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/admin/showtimes")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowtimeResponse createShowtime(@Valid @RequestBody ShowtimeRequest request) {
        return catalogService.createShowtime(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/admin/seat-types")
    @ResponseStatus(HttpStatus.OK)
    public List<SeatTypeResponse> getAllSeatTypes() {
        return catalogService.getAllSeatTypes();
    }
}
