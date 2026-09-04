package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.booking.internal.*;
import com.bilicki.ticketing.booking.web.HoldRequest;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.catalog.CatalogFacade;
import com.bilicki.ticketing.common.ForbiddenActionException;
import com.bilicki.ticketing.config.BookingProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final HoldRepository holdRepository;
    private final BookingMapper bookingMapper;
    private final CatalogFacade catalogFacade;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingProperties bookingProperties;

    @Transactional
    public HoldResponse createHold(UUID showtimeId, UUID userId, HoldRequest request) {
        long distinctSeats = request.showtimeSeatIds().stream().distinct().count();
        if (distinctSeats != request.showtimeSeatIds().size()) {
            throw new DuplicateSeatsException();
        }

        BigDecimal totalPrice = catalogFacade.reserveShowtimeSeats(showtimeId, request.showtimeSeatIds());

        Hold hold = new Hold(showtimeId, userId, totalPrice, Instant.now(clock).plus(bookingProperties.hold().ttl()));

        request.showtimeSeatIds().forEach(showtimeSeatId -> hold.getSeats().add(new HoldSeat(hold, showtimeSeatId)));

        Hold savedHold = holdRepository.save(hold);

        eventPublisher.publishEvent(new HoldExpiryMessage(savedHold.getId(), MDC.get("correlationId")));

        return bookingMapper.toHoldResponse(savedHold);
    }

    @Transactional
    public void transitionHoldStatusFromActiveTo(UUID holdId, Hold.HoldStatus status) {
        Hold hold = holdRepository.findAndLockById(holdId).orElseThrow();

        if (hold.getStatus() == Hold.HoldStatus.ACTIVE) {
            hold.setStatus(status);

            List<UUID> showtimeSeatIds = hold.getSeats().stream().map(HoldSeat::getShowtimeSeatId).toList();

            catalogFacade.releaseShowtimeSeats(hold.getShowtimeId(), showtimeSeatIds);
        }
    }

    @Transactional
    public void cancelHold(UUID holdId, UUID userId) {
        Hold hold = holdRepository.findAndLockById(holdId).orElseThrow(HoldNotFoundException::new);

        if (!hold.getUserId().equals(userId)) {
            throw new ForbiddenActionException("You do not have permission to cancel this hold.");
        }

        if (hold.getStatus() == Hold.HoldStatus.CANCELLED) {
            return;
        }

        if (hold.getStatus() == Hold.HoldStatus.CONFIRMED) {
            throw new HoldAlreadyConfirmedException();
        }

        if (hold.getStatus() == Hold.HoldStatus.EXPIRED) {
            throw new HoldExpiredException();
        }

        hold.setStatus(Hold.HoldStatus.CANCELLED);

        List<UUID> showtimeSeatIds = hold.getSeats().stream().map(HoldSeat::getShowtimeSeatId).toList();
        catalogFacade.releaseShowtimeSeats(hold.getShowtimeId(), showtimeSeatIds);
    }
}
