package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EmptyShowtimeSeatsException extends DomainException {
    public EmptyShowtimeSeatsException(UUID showtimeId) {
        super(
                HttpStatus.NOT_FOUND,
                "showtime-seats-empty",
                "There are no showtime seats generated",
                "There are no Tickets generated for this showtime id: " + showtimeId
        );
    }
}
