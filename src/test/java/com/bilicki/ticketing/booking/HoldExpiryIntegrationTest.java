package com.bilicki.ticketing.booking;

import com.bilicki.ticketing.booking.internal.BookingMapper;
import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.internal.HoldRepository;
import com.bilicki.ticketing.booking.service.BookingService;
import com.bilicki.ticketing.booking.service.HoldExpiredException;
import com.bilicki.ticketing.booking.service.HoldExpiryMessage;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.SeatUnavailableException;
import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.config.BookingProperties;
import com.bilicki.ticketing.user.internal.User;
import com.bilicki.ticketing.user.internal.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("fast-expiry")
@SpringBootTest
@Testcontainers
class HoldExpiryIntegrationTest {

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

    @MockitoSpyBean
    private BookingMapper bookingMapper;

    private Showtime showtime;
    private ShowtimeSeat showtimeSeatA;
    private ShowtimeSeat showtimeSeatB;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;
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

    @Test
    void shouldNotPublishExpiryMessage_WhenTransactionRollsBackAfterPublish() {
        User savedUser = userRepository.save(new User("email2", "pass2"));
        List<UUID> seatIds = List.of(showtimeSeatA.getId());

        doThrow(new RuntimeException("Simulated late failure")).when(bookingMapper).toHoldResponse(any());

        assertThatThrownBy(() -> bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated late failure");

        Object message = rabbitTemplate.receiveAndConvert(bookingProperties.rabbitMq().delayQueueName(), 2000);
        assertThat(message).isNull();
    }

    @Test
    void shouldIgnoreLateExpiryMessage_WhenHoldIsAlreadyConfirmed() {
        User savedUser = userRepository.save(new User("email", "pass"));
        List<UUID> seatIds = List.of(showtimeSeatA.getId());
        HoldResponse holdResponse = bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds));

        Hold hold = holdRepository.findById(holdResponse.holdId()).orElseThrow();
        hold.setStatus(Hold.HoldStatus.CONFIRMED);
        holdRepository.saveAndFlush(hold);

        rabbitTemplate.convertAndSend(bookingProperties.rabbitMq().queueName(), new HoldExpiryMessage(holdResponse.holdId(), "late-corr-id"));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Hold reloadedHold = holdRepository.findById(holdResponse.holdId()).orElseThrow();
            assertThat(reloadedHold.getStatus()).isEqualTo(Hold.HoldStatus.CONFIRMED);

            ShowtimeSeat reloadedSeat = showtimeSeatRepository.findById(showtimeSeatA.getId()).orElseThrow();
            assertThat(reloadedSeat.getStatus()).isEqualTo(ShowtimeSeat.SeatStatus.HELD);
        });
    }

    @Test
    void shouldPreventRaceConditionBetweenCancelAndExpire() throws InterruptedException {
        User savedUser = userRepository.save(new User("email", "pass"));
        List<UUID> seatIds = List.of(showtimeSeatA.getId());
        HoldResponse holdResponse = bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds));
        UUID holdId = holdResponse.holdId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger cancelSuccessCount = new AtomicInteger(0);
        AtomicInteger cancelExceptionCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.cancelHold(holdId, savedUser.getId());
                cancelSuccessCount.incrementAndGet();
            } catch (HoldExpiredException e) {
                cancelExceptionCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.expireHold(holdId, Hold.HoldStatus.EXPIRED);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

        Hold finalHold = holdRepository.findById(holdId).orElseThrow();
        ShowtimeSeat finalSeat = showtimeSeatRepository.findById(showtimeSeatA.getId()).orElseThrow();

        assertThat(finalHold.getStatus()).isIn(Hold.HoldStatus.CANCELLED, Hold.HoldStatus.EXPIRED);

        assertThat(finalSeat.getStatus()).isEqualTo(ShowtimeSeat.SeatStatus.AVAILABLE);

        if (finalHold.getStatus() == Hold.HoldStatus.CANCELLED) {
            assertThat(cancelSuccessCount.get()).isEqualTo(1);
            assertThat(cancelExceptionCount.get()).isEqualTo(0);
        } else {
            assertThat(finalHold.getStatus()).isEqualTo(Hold.HoldStatus.EXPIRED);
            assertThat(cancelSuccessCount.get()).isEqualTo(0);
            assertThat(cancelExceptionCount.get()).isEqualTo(1);
        }
    }

    @Test
    void shouldNotCreateHold_WhenSeatIsAlreadyHeld() {
        User savedUser = userRepository.save(new User("email", "pass"));
        List<UUID> seatIds = List.of(showtimeSeatA.getId());

        bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds));

        rabbitTemplate.receive(bookingProperties.rabbitMq().delayQueueName(), 2000);

        assertThatThrownBy(() -> bookingService.createHold(showtime.getId(), savedUser.getId(), new HoldRequest(seatIds)))
                .isInstanceOf(SeatUnavailableException.class);
    }
}