package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.BookingMapper;
import com.bilicki.ticketing.booking.internal.Hold;
import com.bilicki.ticketing.booking.internal.HoldRepository;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import com.bilicki.ticketing.catalog.SeatUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    private final Instant FIXED_TIME = Instant.parse("2026-08-23T12:00:00Z");
    private final long TTL_MINUTES = 5L;


    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "holdTtlMinutes", TTL_MINUTES);
    }

    @Test
    public void createHold_Success() {
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showtimeSeatAId = UUID.randomUUID();
        UUID showtimeSeatBId = UUID.randomUUID();
        BigDecimal expectedPrice = new BigDecimal("25.00");

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
        assertThat(savedHold.getExpiresAt()).isEqualTo(FIXED_TIME.plus(TTL_MINUTES, ChronoUnit.MINUTES));

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
    }
}
