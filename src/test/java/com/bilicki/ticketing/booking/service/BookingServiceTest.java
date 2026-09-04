package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.BookingMapper;
import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.internal.HoldRepository;
import com.bilicki.ticketing.booking.internal.HoldSeat;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import com.bilicki.ticketing.catalog.SeatUnavailableException;
import com.bilicki.ticketing.common.ForbiddenActionException;
import com.bilicki.ticketing.config.BookingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {
    @Mock
    private HoldRepository holdRepository;
    @Mock
    private Clock clock;
    @Mock
    private CatalogFacade catalogFacade;
    @Spy
    private BookingMapper bookingMapper = Mappers.getMapper(BookingMapper.class);

    @InjectMocks
    private BookingService bookingService;

    @Captor
    private ArgumentCaptor<Hold> holdCaptor;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<HoldExpiryMessage> eventCaptor;

    private final Instant FIXED_TIME = Instant.parse("2026-08-23T12:00:00Z");
    private final Duration TTL = Duration.ofMinutes(5);
    private final BookingProperties bookingProperties = new BookingProperties(
            new BookingProperties.Hold(TTL),
            new BookingProperties.RabbitMq("hold.expiry.queue", "hold.expiry.delay.queue",
                    "hold.expiry.exchange", "hold.expiry.key")
    );

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(holdRepository, bookingMapper, catalogFacade, clock, eventPublisher, bookingProperties);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    public void createHold_Success() {
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showtimeSeatAId = UUID.randomUUID();
        UUID showtimeSeatBId = UUID.randomUUID();
        BigDecimal expectedPrice = new BigDecimal("25.00");

        MDC.put("correlationId", "corr-123");

        List<UUID> showtimeSeatIds = List.of(showtimeSeatAId, showtimeSeatBId);

        when(clock.instant()).thenReturn(FIXED_TIME);
        when(catalogFacade.reserveShowtimeSeats(showtimeId, showtimeSeatIds)).thenReturn(expectedPrice);
        when(holdRepository.save(any(Hold.class))).then(returnsFirstArg());

        HoldResponse holdResponse = bookingService.createHold(showtimeId, userId, new HoldRequest(showtimeSeatIds));

        verify(holdRepository).save(holdCaptor.capture());
        Hold savedHold = holdCaptor.getValue();

        assertThat(savedHold.getShowtimeId()).isEqualTo(showtimeId);
        assertThat(savedHold.getUserId()).isEqualTo(userId);
        assertThat(savedHold.getTotalPrice()).isEqualTo(expectedPrice);
        assertThat(savedHold.getExpiresAt()).isEqualTo(FIXED_TIME.plus(TTL));

        assertThat(savedHold.getSeats()).hasSize(2);
        assertThat(savedHold.getSeats().get(0).getShowtimeSeatId()).isEqualTo(showtimeSeatAId);
        assertThat(savedHold.getSeats().get(0).getHold()).isEqualTo(savedHold);
        assertThat(savedHold.getSeats().get(1).getShowtimeSeatId()).isEqualTo(showtimeSeatBId);
        assertThat(savedHold.getSeats().get(1).getHold()).isEqualTo(savedHold);

        assertThat(holdResponse.holdId()).isEqualTo(savedHold.getId());
        assertThat(holdResponse.totalPrice()).isEqualTo(expectedPrice);
        assertThat(holdResponse.holdSeats()).hasSize(2);
        assertThat(holdResponse.holdSeats().get(0).showtimeSeatId()).isEqualTo(showtimeSeatAId);
        assertThat(holdResponse.holdSeats().get(1).showtimeSeatId()).isEqualTo(showtimeSeatBId);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        HoldExpiryMessage publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.holdId()).isEqualTo(savedHold.getId());
        assertThat(publishedEvent.correlationId()).isEqualTo("corr-123");
    }

    @Test
    void createHold_ThrowsSeatUnavailableException_WhenFacadeFails() {
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<UUID> seatIds = List.of(UUID.randomUUID());
        HoldRequest request = new HoldRequest(seatIds);

        when(catalogFacade.reserveShowtimeSeats(showtimeId, seatIds))
                .thenThrow(new SeatUnavailableException("Not available", seatIds));

        assertThrows(SeatUnavailableException.class, () ->
                bookingService.createHold(showtimeId, userId, request)
        );

        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createHold_ThrowsDuplicateSeatException_WhenRequestContainsDuplicates() {
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID duplicateSeatId = UUID.randomUUID();

        HoldRequest request = new HoldRequest(List.of(duplicateSeatId, duplicateSeatId));

        assertThrows(DuplicateSeatsException.class, () ->
                bookingService.createHold(showtimeId, userId, request)
        );

        verify(catalogFacade, never()).reserveShowtimeSeats(any(), any());
        verify(holdRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createHold_PublishesEventWithNullCorrelationId_WhenMdcIsEmpty() {
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<UUID> showtimeSeatIds = List.of(UUID.randomUUID());

        when(clock.instant()).thenReturn(FIXED_TIME);
        when(catalogFacade.reserveShowtimeSeats(showtimeId, showtimeSeatIds)).thenReturn(BigDecimal.TEN);
        when(holdRepository.save(any(Hold.class))).then(returnsFirstArg());

        bookingService.createHold(showtimeId, userId, new HoldRequest(showtimeSeatIds));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().correlationId()).isNull();
    }

    @Test
    void transitionHoldStatusFromActiveTo_ReleasesSeatsAndUpdatesStatus_WhenHoldIsActive() {
        UUID holdId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID seatAId = UUID.randomUUID();
        UUID seatBId = UUID.randomUUID();

        Hold hold = new Hold(showtimeId, UUID.randomUUID(), new BigDecimal("20.00"), Instant.now());
        hold.getSeats().add(new HoldSeat(hold, seatAId));
        hold.getSeats().add(new HoldSeat(hold, seatBId));

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.transitionHoldStatusFromActiveTo(holdId, Hold.HoldStatus.EXPIRED);

        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.EXPIRED);
        verify(catalogFacade).releaseShowtimeSeats(showtimeId, List.of(seatAId, seatBId));
    }

    @Test
    void transitionHoldStatusFromActiveTo_DoesNothing_WhenHoldIsAlreadyConfirmed() {
        UUID holdId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CONFIRMED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.transitionHoldStatusFromActiveTo(holdId, Hold.HoldStatus.EXPIRED);

        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.CONFIRMED);
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void transitionHoldStatusFromActiveTo_DoesNothing_WhenHoldIsAlreadyExpired() {
        UUID holdId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.EXPIRED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.transitionHoldStatusFromActiveTo(holdId, Hold.HoldStatus.EXPIRED);

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void transitionHoldStatusFromActiveTo_ThrowsNoSuchElementException_WhenHoldNotFound() {
        UUID holdId = UUID.randomUUID();
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                bookingService.transitionHoldStatusFromActiveTo(holdId, Hold.HoldStatus.EXPIRED));

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_Success() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        Hold hold = new Hold(showtimeId, userId, BigDecimal.TEN, Instant.now());
        hold.getSeats().add(new HoldSeat(hold, seatId));

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.cancelHold(holdId, userId);

        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.CANCELLED);
        verify(catalogFacade).releaseShowtimeSeats(showtimeId, List.of(seatId));
    }

    @Test
    void cancelHold_Idempotent_WhenAlreadyCancelled() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), userId, BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CANCELLED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.cancelHold(holdId, userId);

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_ThrowsForbidden_WhenUserMismatch() {
        UUID holdId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), ownerId, BigDecimal.TEN, Instant.now());

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThrows(ForbiddenActionException.class, () ->
                bookingService.cancelHold(holdId, hackerId)
        );
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_ThrowsHoldExpired_WhenHoldIsExpired() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), userId, BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.EXPIRED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThrows(HoldExpiredException.class, () ->
                bookingService.cancelHold(holdId, userId)
        );
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_ThrowsHoldAlreadyConfirmed_WhenHoldIsConfirmed() {
        UUID holdId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Hold hold = new Hold(UUID.randomUUID(), userId, BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CONFIRMED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThrows(HoldAlreadyConfirmedException.class, () ->
                bookingService.cancelHold(holdId, userId)
        );
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }
}
