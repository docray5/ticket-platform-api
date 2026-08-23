package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.*;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

        // TODO create a message in rabbit MQ (Later)

        return bookingMapper.toHoldResponse(savedHold);
    }
}
