package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HoldNotFoundException extends DomainException {
    public HoldNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "hold-not-found",
                "Hold Not Found",
                "The requested hold could not be found."
        );
    }
}
