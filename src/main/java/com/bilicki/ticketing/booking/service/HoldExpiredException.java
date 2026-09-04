package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HoldExpiredException extends DomainException {
    public HoldExpiredException() {
        super(
                HttpStatus.GONE,
                "hold-already-expired",
                "Hold already expired",
                "The requested hold is already expired."
        );
    }
}
