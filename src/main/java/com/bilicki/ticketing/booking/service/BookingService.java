package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.*;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final HoldRepository holdRepository;
    private final BookingMapper bookingMapper;
    private final CatalogFacade catalogFacade;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${booking.hold.ttl-minutes}")
    private long holdTtlMinutes;

    @Transactional
    public HoldResponse createHold(UUID showtimeId, UUID userId, HoldRequest request) {
        long distinctSeats = request.showtimeSeatIds().stream().distinct().count();
        if (distinctSeats != request.showtimeSeatIds().size()) {
            throw new DuplicateSeatsException();
        }

        BigDecimal totalPrice = catalogFacade.reserveShowtimeSeats(showtimeId, request.showtimeSeatIds());

        Hold hold = new Hold(showtimeId, userId, totalPrice, Instant.now(clock).plus(holdTtlMinutes, ChronoUnit.MINUTES));

        request.showtimeSeatIds().forEach(showtimeSeatId -> hold.getSeats().add(new HoldSeat(hold, showtimeSeatId)));

        Hold savedHold = holdRepository.save(hold);

        eventPublisher.publishEvent(new HoldExpiryMessage(
                        savedHold.getId(), showtimeId,
                        savedHold.getSeats().stream().map(HoldSeat::getShowtimeSeatId).toList(),
                        MDC.get("correlationId"))
        );

        return bookingMapper.toHoldResponse(savedHold);
    }
}
