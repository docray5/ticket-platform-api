package com.bilicki.ticketing.common.outbox;

import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.internal.HoldRepository;
import com.bilicki.ticketing.booking.service.BookingService;
import com.bilicki.ticketing.booking.service.HoldExpiryMessage;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.internal.*;
import com.bilicki.ticketing.config.BookingProperties;
import com.bilicki.ticketing.user.internal.User;
import com.bilicki.ticketing.user.internal.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ActiveProfiles("fast-expiry")
@SpringBootTest
@Testcontainers
public class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMq = new RabbitMQContainer("rabbitmq:4-management");

    @MockitoSpyBean
    private BookingService bookingService;

    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxMessageRepository outboxRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private BookingProperties bookingProperties;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;

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
    @Autowired
    private UserRepository userRepository;

    private boolean brokerPaused = false;

    @AfterEach
    void cleanUp() {
        unpauseBroker();

        outboxRepository.deleteAllInBatch();
        amqpAdmin.purgeQueue(bookingProperties.rabbitMq().expiry().delayQueueName(), false);
        amqpAdmin.purgeQueue(bookingProperties.rabbitMq().expiry().queueName(), false);

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
    void recordedEvent_travelsThroughTheDelayQueue_andTheRealListenerReceivesItDeserialized() {
        UUID holdId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> outboxService.recordEvent(
                "", bookingProperties.rabbitMq().expiry().delayQueueName(),
                new HoldExpiryMessage(holdId, "corr-it")));
        UUID rowId = outboxRepository.findAll().getFirst().getId();

        verify(bookingService, timeout(15_000)).expireHold(holdId);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxRepository.findById(rowId).orElseThrow().getStatus())
                        .isEqualTo(OutboxMessage.OutboxStatus.PROCESSED));
    }

    @Test
    void messageForNonexistentQueue_staysPending_withUnroutableError() {
        transactionTemplate.executeWithoutResult(status -> outboxService.recordEvent(
                "", "queue.that.does.not.exist", new HoldExpiryMessage(UUID.randomUUID(), null)));
        UUID rowId = outboxRepository.findAll().getFirst().getId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxMessage row = outboxRepository.findById(rowId).orElseThrow();
            assertThat(row.getLastError()).contains("Unroutable");
            assertThat(row.getStatus()).isEqualTo(OutboxMessage.OutboxStatus.PENDING);
            assertThat(row.getRetryCount()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void createHold_succeedsWhileBrokerIsFrozen_andTheHoldExpiresOnceTheBrokerIsBack() {
        User user = userRepository.save(new User("outage@test.com", "pass"));
        ShowtimeSeat seat = createShowtimeSeat();

        rabbitTemplate.execute(channel -> true);

        pauseBroker();

        HoldResponse hold = bookingService.createHold(
                seat.getShowtime().getId(), user.getId(), new HoldRequest(List.of(seat.getId())));

        assertThat(holdRepository.findById(hold.holdId()).orElseThrow().getStatus()).isEqualTo(Hold.HoldStatus.ACTIVE);
        List<OutboxMessage> rows = outboxRepository.findAll();
        assertThat(rows).hasSize(1);
        UUID rowId = rows.getFirst().getId();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutboxMessage row = outboxRepository.findById(rowId).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(OutboxMessage.OutboxStatus.PENDING);
            assertThat(row.getRetryCount()).isGreaterThanOrEqualTo(1);
        });

        unpauseBroker();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(outboxRepository.findById(rowId).orElseThrow().getStatus())
                    .isEqualTo(OutboxMessage.OutboxStatus.PROCESSED);
            assertThat(holdRepository.findById(hold.holdId()).orElseThrow().getStatus())
                    .isEqualTo(Hold.HoldStatus.EXPIRED);
            assertThat(showtimeSeatRepository.findById(seat.getId()).orElseThrow().getStatus())
                    .isEqualTo(ShowtimeSeat.SeatStatus.AVAILABLE);
        });
    }

    @Test
    void recordEvent_withoutATransaction_throws() {
        assertThatThrownBy(() -> outboxService.recordEvent(
                "", "queue.that.does.not.exist", new HoldExpiryMessage(UUID.randomUUID(), null)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void recordEvent_inATransactionThatRollsBack_leavesNoRow() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxService.recordEvent("", "queue.that.does.not.exist", new HoldExpiryMessage(UUID.randomUUID(), null));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void recordEvent_insideATransaction_storesExchangeRoutingKeyAndJsonPayload() {
        UUID holdId = UUID.randomUUID();
        String delayQueue = bookingProperties.rabbitMq().expiry().delayQueueName();

        transactionTemplate.executeWithoutResult(status ->
                outboxService.recordEvent("", delayQueue, new HoldExpiryMessage(holdId, "corr-1")));

        OutboxMessage row = outboxRepository.findAll().getFirst();
        assertThat(row.getExchange()).isEmpty();
        assertThat(row.getRoutingKey()).isEqualTo(delayQueue);
        assertThat(row.getPayload()).contains(holdId.toString()).contains("corr-1");
    }

    private ShowtimeSeat createShowtimeSeat() {
        Venue venue = venueRepository.save(new Venue("Outage Venue", "Outage street"));
        Hall hall = hallRepository.save(new Hall(venue, "Outage Hall"));
        Movie movie = movieRepository.save(new Movie("Outage Movie", "Desc", 120));
        SeatType type = seatTypeRepository.save(new SeatType("STANDARD", BigDecimal.ONE));
        Seat physicalSeat = seatRepository.save(new Seat(hall, "A", (short) 1, type));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Showtime showtime = showtimeRepository.save(
                new Showtime(movie, hall, start, start.plus(120, ChronoUnit.MINUTES), new BigDecimal("10.00")));

        return showtimeSeatRepository.save(new ShowtimeSeat(showtime, physicalSeat, new BigDecimal("15.00")));
    }

    private void pauseBroker() {
        rabbitMq.getDockerClient().pauseContainerCmd(rabbitMq.getContainerId()).exec();
        brokerPaused = true;
    }

    private void unpauseBroker() {
        if (brokerPaused) {
            rabbitMq.getDockerClient().unpauseContainerCmd(rabbitMq.getContainerId()).exec();
            brokerPaused = false;
        }
    }
}
