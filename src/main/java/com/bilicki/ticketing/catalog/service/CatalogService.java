package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.catalog.internal.CatalogMapper;
import com.bilicki.ticketing.catalog.internal.Venue;
import com.bilicki.ticketing.catalog.internal.VenueRepository;
import com.bilicki.ticketing.catalog.web.VenueRequest;
import com.bilicki.ticketing.catalog.web.VenueResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CatalogService {
    private CatalogMapper catalogMapper;
    private VenueRepository venueRepository;

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

}
