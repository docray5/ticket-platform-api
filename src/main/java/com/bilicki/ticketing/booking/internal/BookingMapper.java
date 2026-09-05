package com.bilicki.ticketing.booking.internal;

import com.bilicki.ticketing.booking.web.BookingResponse;
import com.bilicki.ticketing.booking.web.BookingSeatResponse;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.booking.web.HoldSeatResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {
    HoldSeatResponse toHoldSeatResponse(HoldSeat holdSeat);

    @Mapping(target = "holdId", source = "id")
    @Mapping(target = "holdSeats", source = "seats")
    @Mapping(target = "holdStatus", source = "status")
    HoldResponse toHoldResponse(Hold hold);

    BookingSeatResponse toBookingSeatResponse(BookingSeat bookingSeat);

    @Mapping(target = "bookingId", source = "id")
    BookingResponse toBookingResponse(Booking booking);
}
