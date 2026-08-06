package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.catalog.web.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class CatalogService {
    private final SeatTypeRepository seatTypeRepository;
    private final SeatRepository seatRepository;
    private final HallRepository hallRepository;
    private final CatalogMapper catalogMapper;
    private final VenueRepository venueRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;

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

    public HallResponse createHall(UUID venueId, HallRequest request) {
        if (hallRepository.existsByVenueIdAndName(venueId, request.name()))
            throw new HallConflictException(request.name());

        Venue venue = venueRepository.findById(venueId).orElseThrow(VenueNotFoundException::new);

        Hall hall = new Hall(venue, request.name());

        Hall savedHall = hallRepository.save(hall);

        return catalogMapper.toHallResponse(savedHall);
    }

    public List<HallResponse> getAllHalls() {
        return hallRepository.findAllHallsWithVenues().stream().map(catalogMapper::toHallResponse).toList();
    }

    public List<VenueWithHallsResponse> getAllVenuesWithHalls() {
        return venueRepository.findAllVenuesWithHalls().stream().map(catalogMapper::toVenueWithHallsResponse).toList();
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

    @Transactional
    public Integer bulkGenerateSeats(UUID hallId, SeatGenerationRequest request) {
        Hall hall = hallRepository.findById(hallId).orElseThrow(HallNotFoundException::new);

        if (seatRepository.existsByHallId(hallId))
            throw new SeatsAlreadyGeneratedException(hallId);

        SeatType seatType = seatTypeRepository.findById(request.seatTypeId()).orElseThrow(SeatTypeNotFoundException::new);

        List<Seat> seatsToSave = new ArrayList<>();
        for (int row = 0; row < request.rowCount(); row++) {
            for (short seatNumber = 1; seatNumber <= request.seatsPerRow(); seatNumber++) {
                String rowLabel = Character.toString((char) ('A' + row));

                Seat seat = new Seat(hall, rowLabel, seatNumber, seatType);

                seatsToSave.add(seat);
            }
        }

        List<Seat> saved = seatRepository.saveAll(seatsToSave);
        return saved.size();
    }

    public List<SeatTypeResponse> getAllSeatTypes() {
        return seatTypeRepository.findAll().stream().map(catalogMapper::toSeatTypeResponse).toList();
    }

    /**
     * Before creating a showtime it checks that hall and movie exist and
     * checks if the new showtime isn't overlapping with any other showtime in that Hall
     */
    @Transactional
    public ShowtimeResponse createShowtime(ShowtimeRequest request) {
        Hall hall = hallRepository.findById(request.hallId()).orElseThrow(HallNotFoundException::new);
        Movie movie = movieRepository.findById(request.movieId()).orElseThrow(MovieNotFoundException::new);

        Instant endTime = request.startTime()
                .plusSeconds(movie.getDurationMinutes() * 60L);

        if (showtimeRepository.existsOverlappingShowtime(hall.getId(), request.startTime(), endTime))
            throw new ShowtimeConflictException(request.startTime(), endTime);

        Showtime showtime = new Showtime(movie, hall, request.startTime(), endTime, request.basePrice());

        Showtime savedShowtime = showtimeRepository.save(showtime);

        List<Seat> seats = seatRepository.getAllByHallId(hall.getId());
        if (seats.isEmpty())
            throw new HallEmptyException();

        List<ShowtimeSeat> showtimeSeats = new ArrayList<>();

        seats.forEach(s -> {
            BigDecimal finalPrice = request.basePrice().multiply(s.getSeatType().getPriceMultiplier());
            showtimeSeats.add(new ShowtimeSeat(savedShowtime, s, finalPrice));
        });

        showtimeSeatRepository.saveAll(showtimeSeats);

        return catalogMapper.toShowtimeResponse(savedShowtime);
    }

    public MovieResponse getMovieById(UUID movieId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(MovieNotFoundException::new);
        return catalogMapper.toMovieResponse(movie);
    }

    public List<MovieResponse> getAllMovies(Pageable pageable) {
        return movieRepository.findAll(pageable).stream().map(catalogMapper::toMovieResponse).toList();
    }

    public List<ShowtimeResponse> getAllShowtimesByMovieId(UUID movieId) {
        if (!movieRepository.existsById(movieId))
            throw new MovieNotFoundException();
        return showtimeRepository.findAllByMovieId(movieId).stream().map(catalogMapper::toShowtimeResponse).toList();
    }

    public ShowtimeSeatMapResponse getSeatMapByShowtimeId(UUID showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow(ShowtimeNotFoundException::new);
        if (!seatRepository.existsByHallId(showtime.getHall().getId()))
            throw new HallEmptyException();

        List<ShowtimeSeatResponse> showtimeSeatResponses = showtimeSeatRepository.findAllByShowtimeId(showtimeId)
                .stream().map(catalogMapper::toShowtimeSeatResponse).toList();

        return new ShowtimeSeatMapResponse(showtimeId, catalogMapper.toHallSummaryResponse(showtime.getHall()), showtimeSeatResponses);
    }
}
