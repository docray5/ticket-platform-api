package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HoldAlreadyCancelled extends DomainException {
    public HoldAlreadyCancelled() {
        super(
                HttpStatus.CONFLICT,
                "hold-already-cancelled",
                "Hold Already Cancelled",
                "The requested hold is already cancelled."
        );
    }
}
