package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SeatsAlreadyGeneratedException extends DomainException {
    public SeatsAlreadyGeneratedException(UUID hallId) {
        super(
                HttpStatus.CONFLICT,
                "seats-already-generated",
                "Seats already generated",
                "Seats for this hall: \"" + hallId + "\" have been already generated"
        );
    }
}
