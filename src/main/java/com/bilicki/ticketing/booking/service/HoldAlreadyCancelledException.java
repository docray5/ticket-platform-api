package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HoldAlreadyCancelledException extends DomainException {
    public HoldAlreadyCancelledException() {
        super(
                HttpStatus.CONFLICT,
                "hold-already-cancelled",
                "Hold Already Cancelled",
                "The requested hold is already cancelled."
        );
    }
}
