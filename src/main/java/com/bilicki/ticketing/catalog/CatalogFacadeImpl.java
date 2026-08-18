package com.bilicki.ticketing.catalog;

import com.bilicki.ticketing.catalog.internal.ShowtimeSeat;
import com.bilicki.ticketing.catalog.internal.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {
    private final ShowtimeSeatRepository showtimeSeatRepository;

    private void verifyRequestedSeatsExist(List<ShowtimeSeat> seats, List<UUID> requestedShowtimeSeatIds) throws SeatUnavailableException {
        if (seats.size() != requestedShowtimeSeatIds.size()) {
            Set<UUID> foundIds = seats.stream().map(ShowtimeSeat::getId).collect(Collectors.toSet());
            List<UUID> missingIds = requestedShowtimeSeatIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new SeatUnavailableException("Seats with these IDs do not exist: ", missingIds);
        }
    }

    private void verifySeatStatus(String status, String message, List<ShowtimeSeat> seats) throws SeatUnavailableException {
        List<UUID> unavailableSeatIds = seats
                .stream()
                .filter(showtimeSeat -> !showtimeSeat.getStatus().equals(status))
                .map(ShowtimeSeat::getId)
                .toList();

        if (!unavailableSeatIds.isEmpty())
            throw new SeatUnavailableException(message, unavailableSeatIds);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reserveShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds) {
        List<ShowtimeSeat> reservedSeats = showtimeSeatRepository.findAndLockAllByShowtimeIdAndInSeatIds(showtimeId, showtimeSeatIds);

        verifyRequestedSeatsExist(reservedSeats, showtimeSeatIds);

        verifySeatStatus("AVAILABLE", "One or more requested seats are not available with these IDs: ", reservedSeats);

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (ShowtimeSeat s : reservedSeats) {
            totalPrice = totalPrice.add(s.getPrice());
            s.setStatus("HELD");
        }

        return totalPrice;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds) {
        List<ShowtimeSeat> reservedSeats = showtimeSeatRepository.findAndLockAllByShowtimeIdAndInSeatIds(showtimeId, showtimeSeatIds);

        for (ShowtimeSeat s : reservedSeats)
            if (s.getStatus().equals("HELD"))
                s.setStatus("AVAILABLE");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds) {
        List<ShowtimeSeat> reservedSeats = showtimeSeatRepository.findAndLockAllByShowtimeIdAndInSeatIds(showtimeId, showtimeSeatIds);

        verifyRequestedSeatsExist(reservedSeats, showtimeSeatIds);

        verifySeatStatus("HELD", "Seats with these IDs have been taken or already expired: ", reservedSeats);

        for (ShowtimeSeat s : reservedSeats)
            s.setStatus("BOOKED");
    }
}
