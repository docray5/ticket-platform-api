package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class ShowtimeNotFoundException extends DomainException {
    public ShowtimeNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "showtime-not-found",
                "Showtime Not Found",
                "The requested showtime could not be found."
        );
    }
}
