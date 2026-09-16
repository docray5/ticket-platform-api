package com.bilicki.ticketing.booking.service;

import java.util.UUID;

public record HoldExpiryMessage (
        UUID holdId,
        String correlationId
) {
}
