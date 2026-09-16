- base path: `/api/v1`
- Auth: `Authorization: Bearer <jwt>` on every endpoint except `/auth/register`, `/auth/login`, and public catalog reads
    - `userId` is always derived from the JWT principal.
- This served me more like a plan to visualize this app, before I got to coding.
- this is just for reference as the whole exact docs are available through swagger.

**Auth:** (does not require auth)
- `POST /auth/register`
    - Request:
      `{ "email": "jane@example.com", "password": "asdasd" }`
    - 201 Response
      `{ "id": "<some-uuid>", "email": "jane@example.com" }`
- `POST /auth/login`
    - Request:
      `{ "email": "jane@example.com", "password": "asdasd" }`
    - 200 Response
      `{ "accessToken": "asd123", "expiresIn": 3600}`

**Admin catalog:**
- `POST /admin/venues`
- `POST /admin/venues/{venueId}/halls`
- `POST /admin/movies`
- `POST /admin/showtimes` - creates a showtime for a movie in a hall. This is the point where `ShowtimeSeat` rows are generated (one per physical seat, status `AVAILABLE`).
- `POST /admin/halls/{hallId}/seats:bulk-generate` - rows times seats per row
  - request body:
  ```json
  {
    "rowCount": 5,
    "seatsPerRow": 10,
    "seatTypeId": "<some-uuid>"
  }
  ```
  - response is just an integer - amount of seats generated
- `GET /admin/seat-types`
- `GET /admin/venues` 
- `GET /admin/venues/detailed` - venues with nested halls 
- `GET /admin/halls` 

  **Catalog**
- `GET /movies?page=0&size=20`
    - list of current movies
- `GET /movies/{movieId}`
    - Returns the core details of a single movie.
- `GET /movies/{movieId}/showtimes`
- `GET /showtimes/{showtimeId}/seats`
    - full seat map for a showtime
    - `status` in `AVAILABLE | HELD | BOOKED`
    ```json
    {
      "showtimeId": "<some-uuid>",
      "hall": { "id": "<some-uuid>", "name": "some hall" },
      "showtimeSeats": [
        { "showtimeSeatId": "<some-uuid>", "row": "A", "number": 1, "type": "STANDARD", "price": 12.50, "status": "AVAILABLE" },
        { "showtimeSeatId": "<some-uuid>", "row": "A", "number": 2, "type": "STANDARD", "price": 12.50, "status": "HELD" }
      ]
    }
    ```

Booking (`ROLE_CUSTOMER`)
- `POST /showtimes/{showtimeId}/holds`
    - requires these two headers: Authorization and Idempotency-Key
    - error `409` if one or more seats are no longer available - the body includes exactly which seat IDs conflicted, so the client can re-render the seat map instead of guessing.
    - request
    ```json
    { "showtimeSeatIds": ["<some-uuid>", "<some-uuid>"] }
    ```
    - 201 Response
    ```json
    {
      "holdId": "<some-uuid>",
      "showtimeId": "<some-uuid>",
      "holdSeats": [{ "showtimeSeatId": "<some-uuid>" }],
      "holdStatus": "ACTIVE",
      "totalPrice": 25.00,
      "createdAt": "2026-07-16T10:10:00Z",
      "expiresAt": "2026-07-16T10:15:00Z"
    }
    ```
- `DELETE /holds/{holdId}`
    - when customer releases a hold (returns `204` no content)
- `POST /bookings`
    - `410 Gone` if the hold already expired
    - `402 Payment Required` if the mock payment step declines, replaying a successful request with the same `Idempotency-Key` + identical body returns the original `201`, never a second booking.
    - request with `Idempotency-Key` header
    ```json
    { "holdId": "<some-uuid>", "paymentMethod": "MOCK_CARD" }
    ```
    - 201 Response
    ```json
    {
      "bookingId": "<some-uuid>",
      "showtimeId": "<some-uuid>",
      "totalPrice": 25.00,
      "status": "CONFIRMED",
      "seats": [
        { "showtimeSeatId": "<some-uuid>", "row": "A", "number": 1, "type": "STANDARD", "price": 12.50, "status": "BOOKED" },
        { "showtimeSeatId": "<some-uuid>", "row": "A", "number": 2, "type": "STANDARD", "price": 12.50, "status": "BOOKED" }
      ]
    }
    ```
- `GET /bookings/{bookingId}`
- `GET /bookings`
    - Current users bookings
