package com.bilicki.ticketing.booking.internal;

import com.bilicki.ticketing.booking.web.BookingResponse;
import com.bilicki.ticketing.booking.web.HoldResponse;
import com.bilicki.ticketing.booking.web.HoldSeatResponse;
import com.bilicki.ticketing.catalog.ShowtimeSeatResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookingMapper {
    HoldSeatResponse toHoldSeatResponse(HoldSeat holdSeat);

    @Mapping(target = "holdId", source = "id")
    @Mapping(target = "holdSeats", source = "seats")
    @Mapping(target = "holdStatus", source = "status")
    HoldResponse toHoldResponse(Hold hold);

    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "seats", source = "seatDetails")
    BookingResponse toBookingResponse(Booking booking, List<ShowtimeSeatResponse> seatDetails);
}
