package com.bilicki.ticketing.catalog;

import com.bilicki.ticketing.common.DomainException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class SeatUnavailableException extends DomainException {
    @Getter
    private final List<UUID> unavailableSeatIds;

    public SeatUnavailableException(String message, List<UUID> unavailableSeatIds) {
        super(
                HttpStatus.CONFLICT,
                "seat-unavailable",
                "The requested seats are unavailable.",
                message + unavailableSeatIds
        );
        this.unavailableSeatIds = unavailableSeatIds;
    }
}
