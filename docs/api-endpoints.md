- base path: `/api/v1`
- Auth: `Authorization: Bearer <jwt>` on every endpoint except `/auth/register`, `/auth/login`, and public catalog reads
    - `userId` is always derived from the JWT principal.

**Auth:** (does not require auth)
- `POST /auth/register`
    - Request:
      `{ "email": "jane@example.com", "password": "asdasd" }`
    - 201 Response
      `{ "userId": "awd231", "email": "jane@example.com" }`
- `POST /auth/login`
    - Request:
      `{ "email": "jane@example.com", "password": "asdasd" }`
    - 200 Response
      `{ "accessToken": "asd123", "expiresIn": 3600, "tokenType": "Bearer" }`

**Admin catalog:**
- `POST /admin/venues`
- `POST /admin/venues/{venueId}/halls`
    - defines the row/seat layout once, reused across every showtime in that hall
- `POST /admin/movies`
- `POST /admin/showtimes` - creates a showtime for a movie in a hall. This is the point where `ShowtimeSeat` rows are generated (one per physical seat, status `AVAILABLE`).
- `POST /admin/halls/{hallId}/seats:bulk-generate` - rows times seats per row

**Catalog**
- `GET /movies?page=0&size=20`
    - list of current movies
- `GET /movies/{movieId}`
    - only the venue and the price and time range
    - Returns the core details of a single movie.
    - Response includes: id, title, description, duration_minutes, and created_at.
- `GET /showtimes/{showtimeId}/seats`
    - full seat map for a showtime
    - `status` ∈ `AVAILABLE | HELD | BOOKED`
    ```json
    {
      "showtimeId": "awd2321...",
      "hall": { "name": "Hall 3", "rows": 8, "seatsPerRow": 12 },
      "seats": [
        { "seatId": "s-A1", "row": "A", "number": 1, "type": "STANDARD", "price": 12.50, "status": "AVAILABLE" },
        { "seatId": "s-A2", "row": "A", "number": 2, "type": "STANDARD", "price": 12.50, "status": "HELD" },
        { "seatId": "s-A3", "row": "A", "number": 3, "type": "VIP",      "price": 18.00, "status": "BOOKED" }
      ]
    }
    ```

Booking (`ROLE_CUSTOMER`)
- `POST /showtimes/{showtimeId}/holds`
    - requires these two headers: Authorization and Idempotency-Key
    - error `409` if one or more seats are no longer available - the body includes exactly which seat IDs conflicted, so the client can re-render the seat map instead of guessing.
    - request
    ```json
    { "seatIds": ["s-A1", "s-A2"] }
    ```
    - 201 Response
    ```json
    {
      "holdId": "awdawd123",
      "showtimeId": "awdaydw123-...",
      "seatIds": ["s-A1", "s-A2"],
      "status": "ACTIVE",
      "totalPrice": 25.00,
      "createdAt": "2026-07-16T10:10:00Z",
      "expiresAt": "2026-07-16T10:15:00Z"
    }
    ```
- `DELETE /holds/{holdId}`
    - when customer releases a hold
- `POST /bookings`
    - `410 Gone` if the hold already expired
    - `402 Payment Required` if the mock payment step declines (build a 10% random decline rate so this path is actually reachable in demos/tests). Replaying the same `Idempotency-Key` + identical body returns the original `201`, never a second booking.
    - request
    ```json
    { "holdId": "awdwdas231", "paymentMethod": "MOCK_CARD" }
    ```
    - 201 Response
    ```json
    {
      "bookingId": "bk-4410",
      "status": "CONFIRMED",
      "showtimeId": "awdawdaw123",
      "seats": ["s-A1", "s-A2"],
      "totalPrice": 25.00,
      "confirmedAt": "2026-07-16T10:12:30Z"
    }
    ```
- `GET /bookings/{bookingId}`
- `GET /bookings`
    - Current users bookings
