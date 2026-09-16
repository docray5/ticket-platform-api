package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class BookingNotFoundException extends DomainException {
    public BookingNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "booking-not-found",
                "Booking Not Found",
                "The requested booking could not be found."
        );
    }
}
