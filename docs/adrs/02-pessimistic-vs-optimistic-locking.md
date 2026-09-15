# ADR 02 Locking to handle high concurrency race conditions

## Context
Reserving a seat (`CatalogFacade.reserveShowtimeSeats`) is the highest contention operation in the system. For a popular showtime many people might race for the same seats within the same millisecond (especially when it's the last seat). So the strategy that is picked has to guarantee that there will be exactly one winner and the rest fail cleanly, no matter how many threads race.

I considered two standard approaches:
- Optimistic locking - a `@Version` column 
- Pessimistic locking - `SELECT ... FOR UPDATE`, losers block briefly then fail

## Decision
In short, `ShowtimeSeat` pessimistic lock exists to solve a high concurrency race condition, while the `Hold` uses the same pessimistic locking technique for its actual concurrency guarantee, but optimistic lock is layered on top purely as a safety net against a future path that forgets to lock.
`ShowtimeSeat` reservation, release, and confirmation all take a `PESSIMISTIC_WRITE` lock inside the caller's transaction. Catalog's facade methods run with `Propagation.MANDATORY`, so the lock is always taken inside booking's existing transaction rather than a separate one.

`Hold`'s real concurrency guarantee comes from the same `findAndLockById` pessimistic pattern used for `ShowtimeSeat`. `@Version` exists as a safety net, just in case a future code change tries to update a `Hold` but forgets to grab the pessimistic lock first, not because `Hold`'s access pattern needed a different primary strategy, but because a `Hold` row is only ever touched by its owner (through cancel/confirm) and the expiry consumer, never by an unbounded number of concurrent strangers the way a trending seat is.

See `HoldLifeCycleIntegrationTest.shouldPreventRaceConditionBetweenConfirmAndExpire` for the executable proof of the concurrency race condition.

## Consequences
Pros:
- Both pessimistic and optimistic lock solve our problem, but for seat reservation (high concurrency i.e. will be under high load), pessimistic lock does it more efficiently. Under optimistic locking, N threads racing for the last seat would mean N−1 failed commits that each have to retry, re-check availability, and potentially fail again. Pessimistic locking means losers block, then see the correct up-to-date state and fail once, cleanly.
- Simpler failure. A caller either gets the lock and succeeds, or gets a `409` Seat Unavailable, so there is no "commit conflict, please retry" logic needed on the client.
- Consistent lock ordering (`ORDER BY id`) avoids deadlocks when two requests target overlapping seat sets in different orders.

Negatives:
- Locks are held for the duration of the transaction, so `createHold` / `confirmHold` must stay short, so synchronous external calls (payment, notifications) shouldn't happen while a seat lock is held. However, my implementation does call a synchronous payment operation using `REQUIRES_NEW`. While it executes fast enough since it's a mock, this propagation strategy ties up two database connections simultaneously for the duration of confirmHold (the suspended outer connection holding the lock, plus the inner connection for the payment). When moving to a real payment gateway, this must change to an async payment-result callback to prevent connection pool exhaustion and long-held row locks.
- Doesn't scale horizontally the way optimistic locking does. Acceptable here since this is a single-database monolith, but a real trade-off if seat state were ever split into its own service.

## Alternatives considered:
- Optimistic locking on `ShowtimeSeat` too was rejected. The expected contention pattern (many customers, a handful of hot rows, right before a showtime sells out) is exactly the case optimistic locking handles worst. Apart from that there is no risk that "someone forgot to lock" the `ShowtimeSeat` (like with the `Hold` which has a few paths for mutation), as every mutation path for it goes through the same single facade gated by pessimistic locking. 
- A distributed lock (e.g. Redis) was rejected as unnecessary infrastructure. It would only make sense if seat state moved out of the single Postgres instance and that's already the source of truth for everything else.