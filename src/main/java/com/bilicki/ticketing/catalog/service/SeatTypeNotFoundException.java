package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class SeatTypeNotFoundException extends DomainException {
    public SeatTypeNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "seat-not-found",
                "Seat Not Found",
                "The requested seat could not be found."
        );
    }
}
