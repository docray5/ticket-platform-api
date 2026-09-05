package com.bilicki.ticketing.booking.web;

import com.bilicki.ticketing.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {
    private final BookingService bookingService;

    @PostMapping(path = "/showtimes/{showtimeId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@PathVariable UUID showtimeId, @AuthenticationPrincipal String userIdString, @Valid @RequestBody HoldRequest request) {
        return bookingService.createHold(showtimeId, UUID.fromString(userIdString), request);
    }

    @DeleteMapping(path = "/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelHold(@PathVariable UUID holdId, @AuthenticationPrincipal String userIdString) {
        bookingService.cancelHold(holdId, UUID.fromString(userIdString));
    }

    @PostMapping(path = "/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse confirmHold(@AuthenticationPrincipal String userIdString, @Valid @RequestBody BookingRequest request) {
        return bookingService.confirmHold(UUID.fromString(userIdString), request);
    }
}
