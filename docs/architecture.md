# Architecture explanation

## Package structure (Modular Monolith)
This backend is a single deployable application, and it is divided by modules which represent business domains. The module boundaries are kept by a convention as of right now.

- `catalog`: Venues, Halls, Movies, Showtimes, and the seat map create/read model
- `booking`: Holds, Bookings, seat state transitions
- `payment`: Mock payment processing
- `notification`: Async consumers for post-booking events
- `user`: Registration, authentication, and JWT issuance
- `common`: ProblemDetail mapping, Correlation IDs, Idempotency Mechanics and Transactional Outbox pattern (`common.outbox`)
- `config`: Security config, RabbitMQ topology
- A module never imports another module's repository or entity classes directly. Cross module access must route through a public interface (Facade) exposed at the root of the owning package.

(for full reasoning on why I chose Modular Monolith, see: [ADR 01](/docs/adrs/01-modular-monolith-vs-microservices.md))

## Security

### Auth strategy: Stateless JWT signed symmetrically using HS256
- `POST /auth/register` - validates input, BCrypt-hashes the password
- `POST /auth/login` - verifies credentials, issues a self-signed HS256 JWT with claims `sub` (userId), `role`, `iat`, `exp`
- A `JwtAuthenticationFilter`, registered ahead of Spring Security's default filters, reads `Authorization: Bearer <token>`, validates signature + expiry, and populates the `SecurityContext` with the userId and role.
- Controllers retrieve the current user via `@AuthenticationPrincipal`
- Admin endpoints are gated with `@PreAuthorize("hasRole('ADMIN')")`

### Role based access control
- `CUSTOMER` — default on registration
- `ADMIN` — seeded manually, gates catalog-management endpoints

### Deliberately simplified for v1
- No refresh tokens - short-lived access token only, re-login on expiry
- No OAuth2
- HS256 (shared secret) instead of RS256 (asymmetric)
- No rate limiting on login/register in v1
- No password reset flow
- CSRF disabled - since this is a stateless, token-authenticated API with no browser session/cookie auth, so CSRF (which protects session-cookie-based auth) doesn't apply

## Error handling (built using the RFC 7807 standard)
- Domain Exceptions
  - All business logic errors inherit from an abstract `DomainException` base class, which maps directly to specific HTTP statuses
- Global Controller Advice
  - A single `@RestControllerAdvice` in the `common` package translates exceptions into structured `ProblemDetail` payloads
- Security Exceptions
  - `401 Unauthorized` and `403 Forbidden` errors triggered by Spring Security are intercepted by custom `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` components to ensure they also return standard RFC 7807 JSON.
- Logging vs. response
  - Client Visibility:
    - The client receives a safe, actionable detail message (e.g., "Seats s-A1 are already held") along with a unique `correlationId`. 5xx errors never leak internals like SQL statements or entity field names.
  - Server Visibility:
    - The server logs the full stack trace alongside the same correlationId to trace the failure path

Example:
```json
{
  "detail": "You do not have permission to access this resource.",
  "instance": "/api/v1/admin/venues",
  "status": 403,
  "title": "Access Denied",
  "type": "https://api.ticketing.dev/errors/access-denied",
  "correlationId": "68e18f24-a995-4d90-9e8a-7ba5dd04f66e"
}
```

## Idempotency
State mutating endpoints (`POST /showtimes/{id}/holds`, `POST /bookings`) go through a custom `IdempotencyFilter` before they ever reach a controller. The point is boring but important: a client that times out and retries, or a user who double-clicks the pay button, shouldn't be able to create two holds or get charged twice.
- The filter hashes the request body and tries to insert a `PENDING` row into `idempotency_keys` (unique on key, user and endpoint). If that insert fails a row already exists for this key: either from a request still in flight, or one that already finished, so the filter decides what to do next based on whether the hash matches and whether the earlier row is still `PENDING` or already `COMPLETED`
- Any response below `5xx` gets cached and marked `COMPLETED`. A `402` from declined mock payment, `400` from failed validation or `403` from an ownership check all get replayed exactly like a `201` would. That is harmless for `403` and `400`, because retrying the same input fails the same way regardless. The only catch for frontend implementation is that correcting a bad request requires sending a fresh key, since the original key is now permanently bound to that failed attempt. The payment decline case is the one that actually matters in practice, and its retry rules are covered in the ADR 04 (link below).
- For the full mechanics including the concurrent request race and why replay beats reject, see: [ADR 04](/docs/adrs/04-idempotency-key-strategy.md)

## Async Messaging (RabbitMQ)
Hold expiry and booking confirmation notifications both go through RabbitMQ instead of living on the request thread.
- Hold expiry uses a queue-level TTL + dead-letter exchange. Making the hold duration a single, global configurable constant is a deliberate choice. RabbitMQ only checks TTL when a message reaches the head of the queue, so a per-message TTL with variable hold durations would let a long-lived hold block a short one from expiring on time. This is fully explained in the ADR (link below).
- Transactional Outbox:
  - Neither message (hold expiry and booking confirmation) is sent to RabbitMQ from the request thread. Both are written to `outbox_messages` in the same database transaction as the change that caused them, through `OutboxService`, which refuses to run outside a transaction. If the transaction rolls back, the row rolls back too, so there are no ghost messages.
  - A background `OutboxRelay` publishes pending rows and marks them `PROCESSED` only after the broker's publisher confirm. Failures are retried with exponential backoff, and a broker that is simply unreachable doesn't consume retries. For details see [ADR 05](/docs/adrs/05-transactional-outbox.md).
  - The delivery is at least once, so consumers must tolerate duplicates (`expireHold` re-checks the hold's status under its row lock).
- For more info, see: [ADR 03](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md) and [ADR 05](/docs/adrs/05-transactional-outbox.md)

## Locking (Concurrency Control)
For defenses against race conditions I have implemented both pessimistic and optimistic locks.
- `ShowtimeSeat` rows take a pessimistic lock (`PESSIMISTIC_WRITE`) on reservation, confirmation, and release. So when two requests try to reserve the same seat in the same millisecond, the loser blocks at the db level and exits cleanly, but only once the winner transaction commits. And as opposed to optimistic lock this avoids the retry problem (this is explained further in the ADR)
- `Hold` is also pessimistically locked for every real state transition (cancel, confirm, expire), same technique, same reason. It additionally carries an optimistic `@Version` column, but that's a safety net against a future code change that bypasses the lock.
- For the full info about this, see: [ADR 02](/docs/adrs/02-pessimistic-vs-optimistic-locking.md)

## End to end seat reservation flow

1. First a client hits the server with a `POST /showtimes/{id}/holds` request, that contains an `Idempotency-Key` header (that is not yet in the database)
2. Then, the `IdempotencyFilter` hashes the body and tries to insert a row with `PENDING` status into the `idempotency_keys` table, and succeeds since this is a fresh request.
3. We move from the controller to service layer now and `BookingService.createHold` opens a transaction and calls `CatalogFacade.reserveShowtimeSeats(...)`. That facade method runs with `Propagation.MANDATORY`, so the pessimistic lock it takes on the seat rows happens inside booking's already open transaction rather than a separate one.
4. After correctly locking the seats, the `booking` module writes the `Hold` row with a "now + 5 minutes" `expiresAt` time stamp.
5. In the same transaction, `OutboxService.recordEvent` inserts a `PENDING` row containing the `HoldExpiryMessage`.
6. The transaction commits: the held seats, the hold and the outbox row become visible together and the locks release. The `IdempotencyFilter` stores the `201`. RabbitMQ hasn't been involved yet.
7. Within about a second, `OutboxRelay` claims the row (`FOR UPDATE SKIP LOCKED`, so two instances never take the same row), publishes it to `hold.expiry.delay.queue`, waits for the broker confirm and marks it `PROCESSED`. If the broker is down, the row stays pending and is retried.
8. The message sits on the delay queue for 5 minutes (the TTL starts when the relay publishes), then dead letters to the real queue.
9. And finally the `HoldMessageListener` consumes the expiry message and calls `BookingService.expireHold`. This one uses pessimistic lock on the `Hold`, changes its status to `EXPIRED` if it's still `ACTIVE`, and "releases the seats" by changing their status to `AVAILABLE` through `CatalogFacade`. But if the user's confirm request would have won the race, the listener just finds a hold that's already `CONFIRMED` and does nothing.