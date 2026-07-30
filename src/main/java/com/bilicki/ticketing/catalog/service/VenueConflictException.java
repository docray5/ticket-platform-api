package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class VenueConflictException extends DomainException {
    public VenueConflictException(String name, String address) {
        super(
                HttpStatus.CONFLICT,
                "venue-conflict",
                "Venue Already Exists",
                "A venue with this name: \"" + name + "\" or address: \"" + address + "\" already exists in the system."
        );
    }
}
