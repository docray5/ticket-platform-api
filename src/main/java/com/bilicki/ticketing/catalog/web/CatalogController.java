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
}
