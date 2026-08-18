package com.bilicki.ticketing.catalog;

import com.bilicki.ticketing.catalog.internal.*;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    public void shouldPreventDoubleBookingWhenTwoUsersRequestSameSeatConcurrently() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable concurrentTask = () -> {
            try {
                startLatch.await();

                transactionTemplate.execute(status ->
                        catalogFacade.reserveShowtimeSeats(showtime.getId(), List.of(showtimeSeat1.getId()))
                );

                successCount.incrementAndGet();
            } catch (SeatUnavailableException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(concurrentTask);
        executor.submit(concurrentTask);

        startLatch.countDown();

        doneLatch.await(2, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldPreventRaceConditionBetweenHoldExpiryAndBookingConfirmation() throws InterruptedException {
        showtimeSeat1.setStatus("HELD");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                startLatch.await();

                transactionTemplate.execute(status -> {
                    catalogFacade.releaseShowtimeSeats(showtime.getId(), List.of(showtimeSeat1.getId()));
                    return null;
                });
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        AtomicInteger exceptionCount = new AtomicInteger(0);
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    catalogFacade.confirmShowtimeSeats(showtime.getId(), List.of(showtimeSeat1.getId()));
                    return null;
                });
            } catch (SeatUnavailableException e) {
                exceptionCount.incrementAndGet();
            } catch (Exception ignored) { } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(2, TimeUnit.SECONDS);

        ShowtimeSeat finalSeat = showtimeSeatRepository.findById(showtimeSeat1.getId()).orElseThrow();

        assertThat(finalSeat.getStatus()).isIn("BOOKED", "AVAILABLE");

        if (finalSeat.getStatus().equals("AVAILABLE")) {
            assertThat(exceptionCount.get()).isEqualTo(1);
        } else {
            assertThat(finalSeat.getStatus()).isEqualTo("BOOKED");
            assertThat(exceptionCount.get()).isEqualTo(0);
        }
    }

    @Test
    public void shouldReleaseHeldSeatsBackToAvailable() {
        showtimeSeat1.setStatus("HELD");
        showtimeSeat2.setStatus("HELD");
        showtimeSeatRepository.saveAllAndFlush(List.of(showtimeSeat1, showtimeSeat2));

        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), showtimeSeat2.getId());

        transactionTemplate.execute(status -> {
            catalogFacade.releaseShowtimeSeats(showtime.getId(), showtimeSeatIds);
            return null;
        });

        List<ShowtimeSeat> updatedSeats = showtimeSeatRepository.findAllById(showtimeSeatIds);
        assertThat(updatedSeats).extracting(ShowtimeSeat::getStatus).containsOnly("AVAILABLE");
    }

    @Test
    public void shouldSilentlyIgnoreAlreadyBookedOrMissingSeatsDuringRelease() {
        showtimeSeat1.setStatus("BOOKED");
        showtimeSeat2.setStatus("AVAILABLE");
        showtimeSeatRepository.saveAllAndFlush(List.of(showtimeSeat1, showtimeSeat2));

        UUID fakeSeatId = UUID.randomUUID();
        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), showtimeSeat2.getId(), fakeSeatId);

        transactionTemplate.execute(status -> {
            catalogFacade.releaseShowtimeSeats(showtime.getId(), showtimeSeatIds);
            return null;
        });

        ShowtimeSeat reloaded1 = showtimeSeatRepository.findById(showtimeSeat1.getId()).orElseThrow();
        ShowtimeSeat reloaded2 = showtimeSeatRepository.findById(showtimeSeat2.getId()).orElseThrow();

        assertThat(reloaded1.getStatus()).isEqualTo("BOOKED");
        assertThat(reloaded2.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    public void shouldDoNothingWhenReleaseListIsEmpty() {
        transactionTemplate.execute(status -> {
            catalogFacade.releaseShowtimeSeats(showtime.getId(), List.of());
            return null;
        });
    }

    @Test
    public void shouldConfirmHeldSeatsToBooked() {
        showtimeSeat1.setStatus("HELD");
        showtimeSeat2.setStatus("HELD");
        showtimeSeatRepository.saveAllAndFlush(List.of(showtimeSeat1, showtimeSeat2));

        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), showtimeSeat2.getId());

        transactionTemplate.execute(status -> {
            catalogFacade.confirmShowtimeSeats(showtime.getId(), showtimeSeatIds);
            return null;
        });

        List<ShowtimeSeat> updatedSeats = showtimeSeatRepository.findAllById(showtimeSeatIds);
        assertThat(updatedSeats).extracting(ShowtimeSeat::getStatus).containsOnly("BOOKED");
    }

    @Test
    public void shouldThrowExceptionWhenConfirmingSeatsThatAreNotHeld() {
        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), showtimeSeat2.getId());

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    catalogFacade.confirmShowtimeSeats(showtime.getId(), showtimeSeatIds);
                    return null;
                })
        )
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining("Seats with these IDs have been taken or already expired")
                .extracting("unavailableSeatIds")
                .asInstanceOf(InstanceOfAssertFactories.list(UUID.class))
                .containsExactlyInAnyOrder(showtimeSeat1.getId(), showtimeSeat2.getId());
    }

    @Test
    public void shouldThrowExceptionWhenConfirmingMissingSeats() {
        UUID fakeSeatId = UUID.randomUUID();
        List<UUID> showtimeSeatIds = List.of(showtimeSeat1.getId(), fakeSeatId);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    catalogFacade.confirmShowtimeSeats(showtime.getId(), showtimeSeatIds);
                    return null;
                })
        )
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining("Seats with these IDs do not exist")
                .extracting("unavailableSeatIds")
                .asInstanceOf(InstanceOfAssertFactories.list(UUID.class))
                .containsExactly(fakeSeatId);
    }
}
