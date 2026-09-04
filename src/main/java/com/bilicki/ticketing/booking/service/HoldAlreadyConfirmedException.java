package com.bilicki.ticketing.booking.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HoldAlreadyConfirmedException extends DomainException {
    public HoldAlreadyConfirmedException() {
        super(
                HttpStatus.CONFLICT,
                "hold-already-confirmed",
                "Hold Already Confirmed",
                "The requested hold is already confirmed."
        );
    }
}
