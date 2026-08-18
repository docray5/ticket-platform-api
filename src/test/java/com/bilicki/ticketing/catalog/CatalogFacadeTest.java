package com.bilicki.ticketing.catalog;

import com.bilicki.ticketing.catalog.internal.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class CatalogFacadeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private SeatTypeRepository seatTypeRepository;
    @Autowired
    private CatalogFacade catalogFacade;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Showtime showtime;
    private ShowtimeSeat showtimeSeat1;
    private ShowtimeSeat showtimeSeat2;

    @BeforeEach
    public void setUp() {
        Venue venue = venueRepository.save(new Venue("Some Venue", "Some street"));
        Hall hall = hallRepository.save(new Hall(venue, "Some Hall"));
        Movie movie = movieRepository.save(new Movie("Some Movie", "Desc", 120));
        SeatType standardType = seatTypeRepository.save(new SeatType("STANDARD", BigDecimal.ONE));

        Seat physicalSeat1 = seatRepository.save(new Seat(hall, "A", (short) 1, standardType));
        Seat physicalSeat2 = seatRepository.save(new Seat(hall, "A", (short) 2, standardType));

        showtime = showtimeRepository.save(new Showtime(
                movie,
                hall,
                Instant.parse("2026-08-18T12:00:00Z"),
                Instant.parse("2026-08-18T12:00:00Z").plus(120, ChronoUnit.MINUTES),
                new BigDecimal("10.00")
        ));

        showtimeSeat1 = showtimeSeatRepository.save(new ShowtimeSeat(showtime, physicalSeat1, new BigDecimal("15.00")));
        showtimeSeat2 = showtimeSeatRepository.save(new ShowtimeSeat(showtime, physicalSeat2, new BigDecimal("15.00")));
    }

    @AfterEach
    public void cleanUp() {
        showtimeSeatRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        seatTypeRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        hallRepository.deleteAllInBatch();
        venueRepository.deleteAllInBatch();
    }

    @Test
    public void shouldLockAndReserveAvailableSeats() {
        List<UUID> showtimeSeatIds = Stream.of(showtimeSeat1, showtimeSeat2).map(ShowtimeSeat::getId).toList();

        BigDecimal totalPrice = transactionTemplate.execute(status ->
                catalogFacade.reserveShowtimeSeats(showtime.getId(), showtimeSeatIds));

        assertThat(totalPrice).isEqualByComparingTo("30.00");

        List<ShowtimeSeat> updatedShowtimeSeats = showtimeSeatRepository.findAllById(showtimeSeatIds);
        assertThat(updatedShowtimeSeats).extracting(ShowtimeSeat::getStatus).containsOnly("HELD");
    }

    @Test
    public void shouldThrowExceptionWhenSeatsAreMissing() {
        UUID fakeSeatId = UUID.randomUUID();
        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), fakeSeatId);

        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                catalogFacade.reserveShowtimeSeats(showtime.getId(), showtimeSeatIds)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining(fakeSeatId.toString())
                .hasMessageNotContaining(showtimeSeat1.getId().toString());
    }

    @Test
    public void shouldThrowExceptionWhenSeatsAreAlreadyHeld() {
        showtimeSeat2.setStatus("HELD");
        showtimeSeatRepository.saveAndFlush(showtimeSeat2);

        List<UUID> showtimeSeatIds = Stream.of(showtimeSeat1, showtimeSeat2).map(ShowtimeSeat::getId).toList();

        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                catalogFacade.reserveShowtimeSeats(showtime.getId(), showtimeSeatIds)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining(showtimeSeat2.getId().toString());

        ShowtimeSeat reloadedA1 = showtimeSeatRepository.findById(showtimeSeat1.getId()).orElseThrow();
        assertThat(reloadedA1.getStatus()).isEqualTo("AVAILABLE");
    }
}
