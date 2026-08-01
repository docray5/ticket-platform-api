package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HallEmptyException extends DomainException {
    public HallEmptyException() {
        super(
                HttpStatus.BAD_REQUEST,
                "hall-empty",
                "Hall Has No Seats",
                "Cannot create a showtime for a hall with no generated seats."
        );
    }
}
