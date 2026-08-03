package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class VenueNotFoundException extends DomainException {
    public VenueNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "venue-not-found",
                "Venue Not Found",
                "The requested venue could not be found."
        );
    }
}
