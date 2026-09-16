CREATE TABLE booking_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    showtime_seat_id UUID NOT NULL REFERENCES showtime_seats(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (booking_id, showtime_seat_id)
);