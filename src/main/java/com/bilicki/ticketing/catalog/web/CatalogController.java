package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
public class CatalogController {
    private CatalogService catalogService;

    @PostMapping(path = "/admin/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueRequest request) {
        return catalogService.createVenue(request);
    }

    @GetMapping(path = "/admin/venues")
    @ResponseStatus(HttpStatus.OK)
    public List<VenueResponse> getAllVenues() {
        return catalogService.getAllVenues();
    }

    @PostMapping(path = "/admin/halls")
    @ResponseStatus(HttpStatus.CREATED)
    public HallResponse createHall(@Valid @RequestBody HallRequest request) {
        return catalogService.createHall(request);
    }

    @GetMapping(path = "/admin/halls")
    @ResponseStatus(HttpStatus.OK)
    public List<HallResponse> getAllHalls() {
        return catalogService.getAllHalls();
    }


    @PostMapping(path = "/admin/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return catalogService.createMovie(request);
    }

    @GetMapping(path = "/admin/movies")
    @ResponseStatus(HttpStatus.OK)
    public List<MovieResponse> getAllMovies() {
        return catalogService.getAllMovies();
    }
}
