package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.catalog.internal.CatalogMapper;
import com.bilicki.ticketing.catalog.internal.Venue;
import com.bilicki.ticketing.catalog.internal.VenueRepository;
import com.bilicki.ticketing.catalog.web.VenueRequest;
import com.bilicki.ticketing.catalog.web.VenueResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {
    @Mock
    private VenueRepository venueRepository;

    @Spy
    private CatalogMapper catalogMapper = Mappers.getMapper(CatalogMapper.class);

    @InjectMocks
    private CatalogService catalogService;

    @Test
    void createVenue_Success() {
        VenueRequest venueRequest = new VenueRequest("Cool Venue", "Some Street 123");

        Venue fakeSavedVenue = new Venue("Cool Venue", "Some Street 123");
        ReflectionTestUtils.setField(fakeSavedVenue, "id", UUID.randomUUID());

        when(venueRepository.existsVenueByName(venueRequest.name())).thenReturn(false);
        when(venueRepository.existsVenueByAddress(venueRequest.address())).thenReturn(false);

        when(venueRepository.save(any(Venue.class))).thenReturn(fakeSavedVenue);

        VenueResponse response = catalogService.createVenue(venueRequest);

        assertNotNull(response);
        assertEquals("Cool Venue", response.name());
        verify(venueRepository, times(1)).save(any(Venue.class));
    }

    @Test
    void createVenue_ThrowsConflict_WhenNameExists() {
        VenueRequest venueRequest = new VenueRequest("Cool Venue", "Some Street 123");
        when(venueRepository.existsVenueByName(venueRequest.name())).thenReturn(true);

        assertThrows(VenueConflictException.class, () -> catalogService.createVenue(venueRequest));

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void createVenue_ThrowsConflict_WhenAddressExists() {
        VenueRequest venueRequest = new VenueRequest("Cool Venue", "Some Street 123");
        when(venueRepository.existsVenueByAddress(venueRequest.address())).thenReturn(true);

        assertThrows(VenueConflictException.class, () -> catalogService.createVenue(venueRequest));

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void getAllVenues_ReturnsList() {
        Venue venue1 = new Venue("Cool Venue", "Some Street 123");
        ReflectionTestUtils.setField(venue1, "id", UUID.randomUUID());

        Venue venue2 = new Venue("Another Venue", "Another Street 123");
        ReflectionTestUtils.setField(venue2, "id", UUID.randomUUID());

        when(venueRepository.findAll()).thenReturn(List.of(venue1, venue2));

        List<VenueResponse> venuesResponse = catalogService.getAllVenues();

        assertEquals(venuesResponse.get(0).id(), venue1.getId());
        assertEquals(venuesResponse.get(0).name(), venue1.getName());
        assertEquals(venuesResponse.get(0).address(), venue1.getAddress());

        assertEquals(venuesResponse.get(1).id(), venue2.getId());
        assertEquals(venuesResponse.get(1).name(), venue2.getName());
        assertEquals(venuesResponse.get(1).address(), venue2.getAddress());
    }
}
