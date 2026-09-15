# ADR 04 Idempotency key strategy

## Context
Two endpoints (seat reservation, payment) `POST /showtimes/{id}/holds` and `POST /bookings` both have real world side effects and must be safe to retry. If a client times out and resends the same request (or user double clicks the pay button) the server mustn't create two holds or two bookings, and a client that never received the original response must still be able to learn what actually happened. This required deciding where idempotency keys live (Postgres vs Redis) and what happens on a retry (replay the original response or reject the second request).

## Decision
Idempotency is enforced by a servlet filter (`IdempotencyFilter`), backed by a postgres table (`idempotency_keys`, with unique on `key`, `user_id` and `endpoint`), using an insert-as-lock pattern:
1. Client sends `Idempotency-Key: <uuid>` header. The filter hashes the request body (using SHA-256) and attempts to insert a row into that table.
2. when insert succeeds the filter knows that this is a new request. The filter lets it through, captures the real HTTP response, and if the status code is below `5xx` it updates the row status to `COMPLETED`, with the exact status + body after the request finishes.
3. When insert fails (violation of the unique constraint) then the filter knows that a row exists with the same `key`, `user_id` and `endpoint`. Then the filter does one of these things depending on the state of the request:
   - When the request has a different `request_hash` -> 422 Unprocessable Entity, same key reused for a different request. This is treated as a bug worth surfacing loudly.
   - If it has the same hash, and its status is still `PENDING` -> 409 Conflict, a concurrent duplicate request is still in flight.
   - And also if it has the same hash, but its status is `COMPLETED` -> replay the exact cached response (status code + body), without re-running any business logic.
4. If the underlying request fails with a `5xx` the Pending row is deleted so a genuine retry isn't permanently blocked.

Keys expire after a fixed 24h window (configurable via application.yml), and they are swept by a scheduled cron job `IdempotencyKeyCleanUpTask`.

## Consequences
Pros:
- Correct under concurrency with no extra locking. The race between two identical concurrent requests is resolved for free by the database's own unique constraint enforcement, so there is no need for separate queries, which would have had their own race window. (See `IdempotencyFilterConcurrencyTest.concurrentRequests_WithSameKey_ShouldReturn201And409` for the executable proof of this race)
- Response replay means clients don't need special retry logic. Retrying with the same key is always safe and returns exactly what the original request would have returned.
- No new infrastructure as it reuses the same postgres instance that everything else already depends on.

Negatives:
- Unfortunately a cached failure response is also replayed. For example a `402` payment required from a declined mock payment is cached the same way a `201` would be, so retrying with the same key replays the same decline forever. This creates a strict rule for the frontend: clients must keep the same idempotency key across automatic network timeout retries (to avoid double-charging), but must generate a brand new key when prompting the user to submit an alternative payment method. (This is a deliberate consequence of "replay exactly, don't reprocess", documented explicitly in the [README's](/README.MD) known limitations)
- Storing the full response body as `TEXT` (in the db) works fine at MVP scale but isn't ideal for long term. So if this ever gets huge a reasonable next step would be switching `response_body` to JSONB and partitioning the table by date, or moving to a store with automatic TTL eviction.
- Orphaned `PENDING` rows. A process crash between the `PENDING` insert and request completion leaves an orphaned `PENDING` row that blocks retries with a 409 until the 24h sweep clears it. No shorter timeout or crash-recovery sweep exists yet for this specific case, unlike the general expiry cleanup.

## Alternatives considered:
- Redis, which was rejected for v1, as it is a piece of infrastructure that the project of this scale does not need. It is the more scalable long term answer (auto eviction, avoids a growing postgres table) once the idempotency table becomes a real bottleneck.
- Reject on retry instead of replay, this was rejected because if we throw the same error when we see the same request, the client wouldn't know if their first attempt actually worked. Sending back the exact same response is what makes retries actually safe to automate. 