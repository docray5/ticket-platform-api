package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public class ShowtimeConflictException extends DomainException {
    public ShowtimeConflictException(Instant startTime, Instant endTime) {
        super(
                HttpStatus.CONFLICT,
                "showtime-conflict",
                "Showtime within this time range Already Exists",
                "A showtime within this time frame: \"" + startTime + "\" - \"" + endTime + "\" already exists in the system."
    );
  }
}
