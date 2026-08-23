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
        // TODO idempotency check (Later in the Idempotency Filter)
        return bookingService.createHold(showtimeId, UUID.fromString(userIdString), request);
    }
}
