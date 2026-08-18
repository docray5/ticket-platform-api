package com.bilicki.ticketing;

import com.bilicki.ticketing.catalog.CatalogFacade;
import com.bilicki.ticketing.catalog.SeatUnavailableException;
import com.bilicki.ticketing.catalog.internal.ShowtimeSeat;
import com.bilicki.ticketing.catalog.internal.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {
    private final ShowtimeSeatRepository showtimeSeatRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reserveShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds) {
        List<ShowtimeSeat> reservedSeats = showtimeSeatRepository.findAndLockAllByShowtimeIdAndInSeatIds(showtimeId, showtimeSeatIds);

        if (reservedSeats.size() != showtimeSeatIds.size()) {
            List<UUID> foundIds = reservedSeats.stream().map(ShowtimeSeat::getId).toList();
            List<UUID> missingIds = showtimeSeatIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new SeatUnavailableException(missingIds);
        }

        List<UUID> unavailableSeatIds = reservedSeats
                .stream()
                .filter(showtimeSeat -> !showtimeSeat.getStatus().equals("AVAILABLE"))
                .map(ShowtimeSeat::getId)
                .toList();

        if (!unavailableSeatIds.isEmpty())
            throw new SeatUnavailableException(unavailableSeatIds);

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (ShowtimeSeat s : reservedSeats) {
            totalPrice = totalPrice.add(s.getPrice());
            s.setStatus("HELD");
        }

        return totalPrice;
    }
}
