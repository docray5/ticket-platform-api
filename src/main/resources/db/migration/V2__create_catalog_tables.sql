CREATE TABLE venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE halls (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
   name VARCHAR(100) NOT NULL,
   created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
   UNIQUE (venue_id, name)
);

CREATE TABLE movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE showtimes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movie_id UUID NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    hall_id UUID NOT NULL REFERENCES halls(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    base_price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE seat_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(20) NOT NULL UNIQUE,
    price_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00 CHECK (price_multiplier > 0)
);

CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hall_id UUID NOT NULL REFERENCES halls(id) ON DELETE CASCADE,
    row_label VARCHAR(5) NOT NULL,
    seat_number SMALLINT NOT NULL CHECK (seat_number > 0),
    seat_type_id UUID NOT NULL REFERENCES seat_types(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (hall_id, row_label, seat_number)
);

CREATE TABLE showtime_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    showtime_id UUID NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id),
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (showtime_id, seat_id)
);