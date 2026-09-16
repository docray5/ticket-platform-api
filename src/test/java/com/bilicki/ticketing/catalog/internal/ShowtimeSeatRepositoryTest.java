package com.bilicki.ticketing.catalog.internal;

import com.bilicki.ticketing.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@DataJpaTest
@Import(ClockConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ShowtimeSeatRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private SeatTypeRepository seatTypeRepository;
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private Clock clock;

    @Test
    void findAllByShowtimeId_FetchesSeatsAndTypes() {
        Venue venue = venueRepository.save(new Venue("Test Venue", "123 St"));
        Hall hall = hallRepository.save(new Hall(venue, "Hall 1"));
        Movie movie = movieRepository.save(new Movie("Some Movie", "cool movie", 120));
        Instant startTime = Instant.now(clock);
        Showtime showtime = showtimeRepository.save(new Showtime(movie, hall, startTime, startTime.plusSeconds(3600), BigDecimal.TEN));

        SeatType seatType = new SeatType("Vip", BigDecimal.TEN);
        seatType = seatTypeRepository.save(seatType);

        Seat seat = seatRepository.save(new Seat(hall, "A", (short) 1, seatType));
        showtimeSeatRepository.save(new ShowtimeSeat(showtime, seat, BigDecimal.TEN));

        List<ShowtimeSeat> results = showtimeSeatRepository.findAllByShowtimeId(showtime.getId());

        assertEquals(1, results.size());
        assertEquals("A", results.getFirst().getSeat().getRowLabel());
        assertEquals("Vip", results.getFirst().getSeat().getSeatType().getName());
    }
}
