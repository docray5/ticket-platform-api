package com.bilicki.ticketing.booking.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookingRequest(
    @NotNull UUID holdId,
    @NotBlank String paymentMethod
) { }
