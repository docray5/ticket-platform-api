package com.bilicki.ticketing.catalog;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SeatUnavailableException extends DomainException {
    public SeatUnavailableException(List<UUID> unavailableSeatIds) {
        super(
                HttpStatus.CONFLICT,
                "seat-unavailable",
                "The requested seats are unavailable.",
                String.join("One or more requested seats are not available with these IDs: ",
                        unavailableSeatIds.stream().map(UUID::toString).collect(Collectors.joining("\n")))
        );
    }
}
