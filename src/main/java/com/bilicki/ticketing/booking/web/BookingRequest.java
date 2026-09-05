package com.bilicki.ticketing.booking.web;

import java.util.UUID;

public record BookingRequest(
    UUID holdId,
    String paymentMethod
) { }
