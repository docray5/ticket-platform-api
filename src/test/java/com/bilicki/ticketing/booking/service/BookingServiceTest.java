package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.*;
import com.bilicki.ticketing.booking.web.BookingRequest;
import com.bilicki.ticketing.booking.web.BookingResponse;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import com.bilicki.ticketing.catalog.SeatUnavailableException;
import com.bilicki.ticketing.common.ForbiddenActionException;
import com.bilicki.ticketing.config.BookingProperties;
import com.bilicki.ticketing.payment.PaymentDeclinedException;
import com.bilicki.ticketing.payment.PaymentFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock
    private PaymentFacade paymentFacade;
    @Mock
    private BookingRepository bookingRepository;

    @Captor
    private ArgumentCaptor<HoldExpiryMessage> eventCaptor;

    private final Instant FIXED_TIME = Instant.parse("2026-08-23T12:00:00Z");
    private final Duration TTL = Duration.ofMinutes(5);
    private final BookingProperties bookingProperties = new BookingProperties(
            new BookingProperties.Hold(TTL),
            new BookingProperties.RabbitMq(
                    new BookingProperties.RabbitMq.Expiry("hold.expiry.queue", "hold.expiry.delay.queue",
                    "hold.expiry.exchange", "hold.expiry.key"),
                    new BookingProperties.RabbitMq.Confirm("hold.confirm.queue",
                            "hold.confirm.exchange", "hold.confirm.key")
            )
    );

    private UUID userId;
    private UUID holdId;
    private UUID showtimeId;
    private Hold hold;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(holdRepository, bookingMapper, catalogFacade, clock, eventPublisher, bookingProperties, paymentFacade, bookingRepository);
        userId = UUID.randomUUID();
        holdId = UUID.randomUUID();
        showtimeId = UUID.randomUUID();
        request = new BookingRequest(holdId, "MOCK_CARD");

        hold = new Hold(showtimeId, userId, new BigDecimal("25.00"), Instant.now().plusSeconds(300));
        ReflectionTestUtils.setField(hold, "id", holdId);
        hold.getSeats().add(new HoldSeat(hold, UUID.randomUUID()));

        lenient().when(clock.instant()).thenReturn(FIXED_TIME);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    public void createHold_Success() {
        UUID showtimeSeatAId = UUID.randomUUID();
        UUID showtimeSeatBId = UUID.randomUUID();
        BigDecimal expectedPrice = new BigDecimal("25.00");

        MDC.put("correlationId", "corr-123");

        List<UUID> showtimeSeatIds = List.of(showtimeSeatAId, showtimeSeatBId);

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
        List<UUID> showtimeSeatIds = List.of(UUID.randomUUID());

        when(catalogFacade.reserveShowtimeSeats(showtimeId, showtimeSeatIds)).thenReturn(BigDecimal.TEN);
        when(holdRepository.save(any(Hold.class))).then(returnsFirstArg());

        bookingService.createHold(showtimeId, userId, new HoldRequest(showtimeSeatIds));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().correlationId()).isNull();
    }

    @Test
    void expireHold_ReleasesSeatsAndUpdatesStatus_WhenHoldIsActive() {
        UUID seatAId = UUID.randomUUID();
        UUID seatBId = UUID.randomUUID();

        Hold hold = new Hold(showtimeId, UUID.randomUUID(), new BigDecimal("20.00"), Instant.now());
        hold.getSeats().add(new HoldSeat(hold, seatAId));
        hold.getSeats().add(new HoldSeat(hold, seatBId));

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.expireHold(holdId);

        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.EXPIRED);
        verify(catalogFacade).releaseShowtimeSeats(showtimeId, List.of(seatAId, seatBId));
    }

    @Test
    void expireHold_DoesNothing_WhenHoldIsAlreadyConfirmed() {
        Hold hold = new Hold(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CONFIRMED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.expireHold(holdId);

        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.CONFIRMED);
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void expireHold_DoesNothing_WhenHoldIsAlreadyExpired() {

        Hold hold = new Hold(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.EXPIRED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.expireHold(holdId);

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void expireHold_ThrowsNoSuchElementException_WhenHoldNotFound() {
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                bookingService.expireHold(holdId));

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_Success() {
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

        Hold hold = new Hold(UUID.randomUUID(), userId, BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CANCELLED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        bookingService.cancelHold(holdId, userId);

        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void cancelHold_ThrowsForbidden_WhenUserMismatch() {
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
        Hold hold = new Hold(UUID.randomUUID(), userId, BigDecimal.TEN, Instant.now());
        hold.setStatus(Hold.HoldStatus.CONFIRMED);

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThrows(HoldAlreadyConfirmedException.class, () ->
                bookingService.cancelHold(holdId, userId)
        );
        verify(catalogFacade, never()).releaseShowtimeSeats(any(), any());
    }

    @Test
    void shouldSuccessfullyConfirmHold_AndCreateBooking() {
        MDC.put("correlationId", "corr-123");

        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));
        when(bookingMapper.toBookingResponse(any(Booking.class))).thenReturn(
                new BookingResponse(UUID.randomUUID(), showtimeId, new BigDecimal("25.00"), "CONFIRMED", List.of())
        );

        BookingResponse response = bookingService.confirmHold(userId, request);

        assertThat(response).isNotNull();
        assertThat(hold.getStatus()).isEqualTo(Hold.HoldStatus.CONFIRMED);

        verify(paymentFacade).pay(holdId, new BigDecimal("25.00"));

        verify(catalogFacade).confirmShowtimeSeats(eq(showtimeId), anyList());

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();

        assertThat(savedBooking.getUserId()).isEqualTo(userId);
        assertThat(savedBooking.getTotalPrice()).isEqualTo(new BigDecimal("25.00"));
        assertThat(savedBooking.getBookingSeats()).hasSize(1);
        assertThat(savedBooking.getBookingSeats().getFirst().getShowtimeSeatId())
                .isEqualTo(hold.getSeats().getFirst().getShowtimeSeatId());

        InOrder inOrder = inOrder(bookingRepository, catalogFacade, paymentFacade);
        inOrder.verify(bookingRepository).save(any());
        inOrder.verify(catalogFacade).confirmShowtimeSeats(any(), any());
        inOrder.verify(paymentFacade).pay(any(), any());

        ArgumentCaptor<HoldConfirmMessage> confirmCaptor = ArgumentCaptor.forClass(HoldConfirmMessage.class);
        verify(eventPublisher).publishEvent(confirmCaptor.capture());
        HoldConfirmMessage publishedConfirm = confirmCaptor.getValue();
        assertThat(publishedConfirm.holdId()).isEqualTo(holdId);
        assertThat(publishedConfirm.userId()).isEqualTo(userId);
        assertThat(publishedConfirm.correlationId()).isEqualTo("corr-123");
    }

    @Test
    void shouldThrowForbidden_WhenUserDoesNotOwnHold() {
        UUID wrongUserId = UUID.randomUUID();
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> bookingService.confirmHold(wrongUserId, request))
                .isInstanceOf(ForbiddenActionException.class)
                .hasMessageContaining("permission");

        verifyNoInteractions(paymentFacade, catalogFacade, bookingRepository, eventPublisher);
    }

    @Test
    void shouldThrowHoldExpired_WhenHoldIsAlreadyExpired() {
        hold.setStatus(Hold.HoldStatus.EXPIRED);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldExpiredException.class);

        verifyNoInteractions(paymentFacade, catalogFacade, bookingRepository, eventPublisher);
    }

    @Test
    void shouldPropagateException_WhenPaymentDeclines() {
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));
        doThrow(new PaymentDeclinedException()).when(paymentFacade).pay(any(), any());

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(PaymentDeclinedException.class);
    }

    @Test
    void cancelHold_ThrowsHoldNotFoundException_WhenHoldNotFound() {
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelHold(holdId, userId))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void confirmHold_ThrowsHoldNotFoundException_WhenHoldNotFound() {
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void confirmHold_ThrowsHoldAlreadyConfirmed_WhenAlreadyConfirmed() {
        hold.setStatus(Hold.HoldStatus.CONFIRMED);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldAlreadyConfirmedException.class);
    }

    @Test
    void confirmHold_ThrowsHoldAlreadyCancelled_WhenAlreadyCancelled() {
        hold.setStatus(Hold.HoldStatus.CANCELLED);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldAlreadyCancelledException.class);
    }

    @Test
    void confirmHold_ThrowsHoldExpired_WhenExpiresAtHasPassed_EvenIfStatusStillActive() {
        Hold staleHold = new Hold(showtimeId, userId, new BigDecimal("25.00"), FIXED_TIME.minusSeconds(5));
        when(clock.instant()).thenReturn(FIXED_TIME);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(staleHold));

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldExpiredException.class);

        verifyNoInteractions(paymentFacade, catalogFacade, bookingRepository);
    }

    @Test
    void confirmHold_ThrowsHoldAlreadyCancelled_NotExpired_WhenCancelledHoldsOriginalExpiryHasPassed() {
        Hold staleCancelledHold = new Hold(showtimeId, userId, new BigDecimal("25.00"), FIXED_TIME.minusSeconds(5));
        staleCancelledHold.setStatus(Hold.HoldStatus.CANCELLED);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(staleCancelledHold));

        assertThatThrownBy(() -> bookingService.confirmHold(userId, request))
                .isInstanceOf(HoldAlreadyCancelledException.class);
    }

    @Test
    void cancelHold_ThrowsHoldExpired_WhenExpiresAtHasPassed_EvenIfStatusStillActive() {
        Hold staleHold = new Hold(showtimeId, userId, new BigDecimal("25.00"), FIXED_TIME.minusSeconds(5));
        when(clock.instant()).thenReturn(FIXED_TIME);
        when(holdRepository.findAndLockById(holdId)).thenReturn(Optional.of(staleHold));

        assertThatThrownBy(() -> bookingService.cancelHold(holdId, userId))
                .isInstanceOf(HoldExpiredException.class);

        verifyNoInteractions(catalogFacade);
    }
}
