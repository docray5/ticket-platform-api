package com.bilicki.ticketing.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CatalogFacade {
    /**
     * locks the requested seats, validates they are AVAILABLE, and changes them to HELD
     * @return the total price of all requested seats
     */
    BigDecimal reserveShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds);

    void releaseShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds);

    void confirmShowtimeSeats(UUID showtimeId, List<UUID> showtimeSeatIds);
}
