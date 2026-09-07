package com.bilicki.ticketing.booking;

import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.internal.HoldRepository;
import com.bilicki.ticketing.booking.service.BookingService;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.config.BookingProperties;
import com.bilicki.ticketing.user.internal.User;
import com.bilicki.ticketing.user.internal.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("fast-expiry")
@SpringBootTest
@Testcontainers
public class HoldExpiryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMq = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private BookingService bookingService;
    @Autowired
    private HoldRepository holdRepository;
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

    private Showtime showtime;
    private ShowtimeSeat showtimeSeatA;
    private ShowtimeSeat showtimeSeatB;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void setUp() {
        Venue venue = venueRepository.save(new Venue("Some Venue", "Some street"));
        Hall hall = hallRepository.save(new Hall(venue, "Some Hall"));
        Movie movie = movieRepository.save(new Movie("Some Movie", "Desc", 120));
        SeatType standardType = seatTypeRepository.save(new SeatType("STANDARD", BigDecimal.ONE));

        Seat physicalSeatA = seatRepository.save(new Seat(hall, "A", (short) 1, standardType));
        Seat physicalSeatB = seatRepository.save(new Seat(hall, "A", (short) 2, standardType));

        showtime = showtimeRepository.save(new Showtime(
                movie,
                hall,
                Instant.parse("2026-08-18T12:00:00Z"),
                Instant.parse("2026-08-18T12:00:00Z").plus(120, ChronoUnit.MINUTES),
                new BigDecimal("10.00")
        ));

        showtimeSeatA = showtimeSeatRepository.save(new ShowtimeSeat(showtime, physicalSeatA, new BigDecimal("15.00")));
        showtimeSeatB = showtimeSeatRepository.save(new ShowtimeSeat(showtime, physicalSeatB, new BigDecimal("15.00")));
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.purgeQueue(bookingProperties.rabbitMq().delayQueueName(), false);
        amqpAdmin.purgeQueue(bookingProperties.rabbitMq().queueName(), false);

        holdRepository.deleteAll();
        showtimeSeatRepository.deleteAll();
        showtimeRepository.deleteAll();
        seatRepository.deleteAll();
        hallRepository.deleteAll();
        venueRepository.deleteAll();
        movieRepository.deleteAll();
        seatTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReleaseSeatsAndExpireHold_WhenHoldTtlElapses() {
        User savedUser = userRepository.save(new User("email", "pass"));
        List<UUID> seatIds = List.of(showtimeSeatA.getId(), showtimeSeatB.getId());

        HoldResponse holdResponse = bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds));

        assertThat(holdRepository.findById(holdResponse.holdId()).orElseThrow().getStatus())
                .isEqualTo(Hold.HoldStatus.ACTIVE);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Hold reloadedHold = holdRepository.findById(holdResponse.holdId()).orElseThrow();
            assertThat(reloadedHold.getStatus()).isEqualTo(Hold.HoldStatus.EXPIRED);

            List<ShowtimeSeat> reloadedSeats = showtimeSeatRepository.findAllById(seatIds);
            assertThat(reloadedSeats).extracting(ShowtimeSeat::getStatus)
                    .containsOnly(ShowtimeSeat.SeatStatus.AVAILABLE);
        });
    }
}
