package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class DuplicateSeatsException extends DomainException {
    public DuplicateSeatsException() {
        super(
                HttpStatus.BAD_REQUEST,
                "duplicate-seats-requested",
                "Duplicate Seats Requested",
                "A single hold request cannot contain duplicate seat IDs."
        );
    }
}