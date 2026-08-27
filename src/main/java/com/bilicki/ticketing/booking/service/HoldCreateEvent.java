package com.bilicki.ticketing.booking.service;

import java.util.List;
import java.util.UUID;

public record HoldCreateEvent(
        UUID holdId,
        UUID showtimeId,
        List<UUID> showtimeSeatIds,
        String correlationId
) {
}
