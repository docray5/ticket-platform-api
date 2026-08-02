package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.catalog.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private HallRepository hallRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private SeatTypeRepository seatTypeRepository;

    @Spy
    private CatalogMapper catalogMapper = Mappers.getMapper(CatalogMapper.class);

    @InjectMocks
    private CatalogService catalogService;

    @Captor
    ArgumentCaptor<List<ShowtimeSeat>> showtimeSeatListCaptor;
    @Captor
    ArgumentCaptor<List<Seat>> seatListCaptor;

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

        assertEquals(venuesResponse.getFirst().id(), venue1.getId());
        assertEquals(venuesResponse.get(0).name(), venue1.getName());
        assertEquals(venuesResponse.get(0).address(), venue1.getAddress());

        assertEquals(venuesResponse.get(1).id(), venue2.getId());
        assertEquals(venuesResponse.get(1).name(), venue2.getName());
        assertEquals(venuesResponse.get(1).address(), venue2.getAddress());
    }

    @Test
    void createShowtime_Success() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);

        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie("Some movie", "desc", 120);
        ReflectionTestUtils.setField(movie, "id", movieId);

        Instant startTime = Instant.parse("2026-08-01T18:00:00Z");
        Instant expectedEndTime = startTime.plus(120, ChronoUnit.MINUTES);
        BigDecimal basePrice = new BigDecimal("25.00");

        ShowtimeRequest request = new ShowtimeRequest(movieId, hallId, startTime, basePrice);

        SeatType standard = new SeatType();
        ReflectionTestUtils.setField(standard, "priceMultiplier", BigDecimal.ONE);
        Seat seat1 = new Seat(hall, "A", (short) 1, standard);
        Seat seat2 = new Seat(hall, "A", (short) 2, standard);

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(showtimeRepository.existsOverlappingShowtime(hallId, startTime, expectedEndTime)).thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(returnsFirstArg());
        when(seatRepository.getAllByHallId(hallId)).thenReturn(List.of(seat1, seat2));

        ShowtimeResponse response = catalogService.createShowtime(request);

        assertNotNull(response);
        assertEquals(movieId, response.movieId());
        assertEquals(hallId, response.hallId());
        assertEquals(startTime, response.startTime());
        assertEquals(expectedEndTime, response.endTime());

        verify(showtimeSeatRepository).saveAll(showtimeSeatListCaptor.capture());
        List<ShowtimeSeat> showtimeSeats = showtimeSeatListCaptor.getValue();

        assertEquals(2, showtimeSeats.size());
        assertEquals(0, basePrice.compareTo(showtimeSeats.getFirst().getPrice()));
    }

    @Test
    void createShowtime_ThrowsNotFound_WhenMovieMissing() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);
        UUID movieId = UUID.randomUUID();

        ShowtimeRequest request = new ShowtimeRequest(movieId, hallId, Instant.now(), BigDecimal.TEN);

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(movieRepository.findById(movieId)).thenReturn(Optional.empty());

        assertThrows(MovieNotFoundException.class, () -> catalogService.createShowtime(request));

        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void createShowtime_ThrowsConflict_WhenOverlapping() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);

        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie("Some movie", "desc", 120);
        ReflectionTestUtils.setField(movie, "id", movieId);

        Instant startTime = Instant.parse("2026-08-01T18:00:00Z");
        ShowtimeRequest request = new ShowtimeRequest(movieId, hallId, startTime, BigDecimal.TEN);

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(showtimeRepository.existsOverlappingShowtime(eq(hallId), any(), any())).thenReturn(true);

        assertThrows(ShowtimeConflictException.class, () -> catalogService.createShowtime(request));

        verify(showtimeRepository, never()).save(any());
    }

    @Test
    void createShowtime_ThrowsHallEmpty_WhenNoSeatsExist() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);

        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie("Some movie", "desc", 120);
        ReflectionTestUtils.setField(movie, "id", movieId);

        Instant startTime = Instant.parse("2026-08-01T18:00:00Z");
        ShowtimeRequest request = new ShowtimeRequest(movieId, hallId, startTime, BigDecimal.TEN);

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(showtimeRepository.existsOverlappingShowtime(eq(hallId), any(), any())).thenReturn(false);
        when(seatRepository.getAllByHallId(hallId)).thenReturn(List.of());

        when(showtimeRepository.save(any(Showtime.class))).then(returnsFirstArg());

        assertThrows(HallEmptyException.class, () -> catalogService.createShowtime(request));

        verify(showtimeSeatRepository, never()).saveAll(any());
    }

    void bulkGenerateSeats_Success() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);


        UUID seatTypeId = UUID.randomUUID();
        SeatType seatType = new SeatType();
        ReflectionTestUtils.setField(seatType, "id", seatTypeId);

        when(seatTypeRepository.findById(seatTypeId)).thenReturn(Optional.of(seatType));
        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(seatRepository.existsByHallId(hallId)).thenReturn(false);
        when(seatRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());

        SeatGenerationRequest request = new SeatGenerationRequest(2, 3, seatTypeId);

        Integer count = catalogService.bulkGenerateSeats(hallId, request);

        assertEquals(6, count);
        verify(seatRepository).saveAll(seatListCaptor.capture());
        List<Seat> savedSeats = seatListCaptor.getValue();

        assertEquals(6, savedSeats.size());
        assertEquals("A", savedSeats.get(0).getRowLabel());
        assertEquals((short) 1, savedSeats.get(0).getSeatNumber());
        assertEquals("A", savedSeats.get(2).getRowLabel());
        assertEquals((short) 3, savedSeats.get(2).getSeatNumber());
        assertEquals("B", savedSeats.get(3).getRowLabel());
        assertEquals((short) 1, savedSeats.get(3).getSeatNumber());
    }

    @Test
    void bulkGenerateSeats_ThrowsNotFound_WhenHallMissing() {
        UUID hallId = UUID.randomUUID();
        SeatGenerationRequest request = new SeatGenerationRequest(2, 3, UUID.randomUUID());

        when(hallRepository.findById(hallId)).thenReturn(Optional.empty());

        assertThrows(HallNotFoundException.class, () -> catalogService.bulkGenerateSeats(hallId, request));

        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void bulkGenerateSeats_ThrowsConflict_WhenSeatsAlreadyExist() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);

        SeatGenerationRequest request = new SeatGenerationRequest(2, 3, UUID.randomUUID());

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(seatRepository.existsByHallId(hallId)).thenReturn(true);

        assertThrows(SeatsAlreadyGeneratedException.class, () -> catalogService.bulkGenerateSeats(hallId, request));

        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void bulkGenerateSeats_ThrowsNotFound_WhenSeatTypeMissing() {
        UUID hallId = UUID.randomUUID();
        Hall hall = new Hall(new Venue("Some Venue", "Address"), "Hall");
        ReflectionTestUtils.setField(hall, "id", hallId);

        UUID seatTypeId = UUID.randomUUID();
        SeatGenerationRequest request = new SeatGenerationRequest(2, 3, seatTypeId);

        when(hallRepository.findById(hallId)).thenReturn(Optional.of(hall));
        when(seatRepository.existsByHallId(hallId)).thenReturn(false);
        when(seatTypeRepository.findById(seatTypeId)).thenReturn(Optional.empty());

        assertThrows(SeatTypeNotFoundException.class, () -> catalogService.bulkGenerateSeats(hallId, request));

        verify(seatRepository, never()).saveAll(anyList());
    }
}
