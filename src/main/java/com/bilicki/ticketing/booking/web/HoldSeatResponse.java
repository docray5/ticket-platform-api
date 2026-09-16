package com.bilicki.ticketing.booking.web;

import java.util.UUID;

public record HoldSeatResponse(
    UUID showtimeSeatId
) { }
