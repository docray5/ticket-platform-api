package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HallNotFoundException extends DomainException {
    public HallNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "hall-not-found",
                "Hall Not Found",
                "The requested hall could not be found."
        );
    }
}
