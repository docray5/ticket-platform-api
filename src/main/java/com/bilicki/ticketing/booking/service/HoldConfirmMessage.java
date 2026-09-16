package com.bilicki.ticketing.booking.service;

import java.util.UUID;

public record HoldConfirmMessage (
        UUID holdId,
        UUID userId,
        String correlationId
) { }
