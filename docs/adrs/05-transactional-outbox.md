# ADR 05 Transactional outbox for reliable message publishing

## Context
Two events leave the booking flow through RabbitMQ: the hold expiry message and the booking confirmation message. Both are caused by a database change (a new `Hold`, a new `Booking`), and both must be sent if and only if that change commits.

A database and a message broker can't share one transaction, so "save, then publish" is a dual write and one of the two steps can always fail on its own.

My first implementation, which published to the broker from an `AFTER_COMMIT` transaction listener, had an issue: if RabbitMQ is down at that moment, the hold and its `HELD` seats are already committed but no expiry message exists. I tested it by stopping RabbitMQ and creating a hold: the seats stayed held and nothing would ever release them.

## Decision
Events are no longer sent to RabbitMQ from the request thread. They are written to a table in the same transaction as the change that caused them, and a background relay publishes them to the broker.

On the write path, `OutboxService.recordEvent` runs with `Propagation.MANDATORY`, so it refuses to run outside a transaction. It inserts a `PENDING` row into `outbox_messages` holding the target exchange, routing key and the JSON payload. If the surrounding transaction rolls back, the row rolls back with it.

Next, `OutboxRelay` is a scheduled poller (fixed delay, default 1s) that drains the table in batches. Each batch is one database transaction:
1. Claim up to `batchSize` due `PENDING` rows, oldest first, with `FOR UPDATE SKIP LOCKED`, so two app instances (for example during a rolling deploy) never take the same row.
2. Send each row with a `CorrelationData` and wait for the broker's publisher confirms, within a single `confirmTimeout` deadline for the batch.
3. Update each row (`PROCESSED`, or a recorded failure) and commit.

A row counts as delivered only if the broker acks it and does not return it as unroutable. This depends on `publisher-confirm-type: correlated`, `publisher-returns: true` and `template.mandatory: true`.

The relay handles three kinds of failure differently:
- if the broker is unreachable (`AmqpConnectException`, `AmqpIOException`), the batch halts. Unsent rows are left untouched and no retry is consumed. Messages already sent in that batch still get their confirms awaited. The next scheduled run tries again.
- if a single message fails (nack, unroutable, confirm timeout, other `AmqpException`), `retry_count` increases, `next_attempt_at` moves forward by an exponential backoff (capped at default 60s), and `last_error` records why. After 8 attempts (configurable) the row becomes `FAILED` and stays in the table for inspection. 
- if nothing in a batch is delivered (sends succeed, but every message times out, is nacked or comes back unroutable, typically because of a hung broker or broken routing), each message is handled like a single message failure and consumes one retry. The relay also stops draining for that run, so a stalled run touches only one batch.

`OutboxCleanupTask` deletes `PROCESSED` rows older than the configured retention (default 7 days). `FAILED` rows are kept.

## Consequences
Pros:
- No ghost messages and no lost messages at the moment of commit: the event exists exactly when the business change exists.
- A RabbitMQ outage no longer fails or rolls back a request. Hold creation succeeds, and the pending rows are published once the broker returns (this holds for an unreachable broker, for a hung broker limited by the retry window see the last bullet in negatives below).
- Safe with multiple instances, because of `SKIP LOCKED` inside the claiming transaction.
- Unroutable messages and broker rejections are detected instead of silently counted as sent.
- Failures are visible and diagnosable (status, retry_count, last_error).
- One mechanism for both events, expiry and booking confirmation.

Negatives:
- Delivery is at least once. A crash after the broker confirms but before the commit, or a confirm timeout followed by a late ack, causes a re-send, so consumers must tolerate duplicates. `expireHold` does because it rechecks the hold's status under its row lock. The confirmation notification does not deduplicate yet, as it's just a mock, however the message id is available for that when a real provider is added.
- Events are published up to one poll interval after commit. For hold expiry the delay queue TTL starts at publish time, so after a long broker outage a hold's seats can stay `HELD` for the outage duration plus the TTL. `confirmHold` still rejects an expired hold, so only seat availability is affected.
- The outbox guarantees the event is recorded, not that it is delivered or acted on: a row can reach `FAILED`, or a message can be lost inside the broker after its confirm, and neither is recovered automatically. The planned reconciliation sweep (see [README.MD](/README.MD)) will be what guarantees expiry, with the outbox plus RabbitMQ as the fast path.
- The relay's transaction is long-ish: it holds one database connection and the claimed row locks while waiting for confirms, bounded by `confirmTimeout` per batch. Nothing else reads or writes these rows, so there is no contention, but it is one pooled connection during the wait.
- The relay polls Postgres every second. It is cheap (a partial index over `PENDING` rows, a load independent of the number of active holds), but it is a poll.
- A hung broker, unlike an unreachable one, consumes retries, because a timeout can't be told apart from a message-level failure. With the defaults (8 attempts, backoff capped at 60s, 3s confirm timeout), the oldest pending rows become `FAILED` after roughly 2-3 minutes of hanging. An unreachable broker is tolerated indefinitely.

## Alternatives considered:
- Publishing directly after commit (`AFTER_COMMIT`) was my first implementation. I rejected it because commit and publish can't be made atomic: a broker outage at the wrong moment leaves committed state with no event.
- Claiming rows, then publishing outside the transaction, would avoid holding a connection during the broker wait. Rejected: row locks end with the claim transaction, so another instance can claim the same rows, and writing results back can overwrite another instance's update. Making it safe needs a lease column and conditional updates. That is not worth the complexity at this scale and can be revisited if broker waits ever contend for connections.
- A reconciliation sweep alone, without RabbitMQ for expiry, would be correct, since `expires_at` in the database is the source of truth, and simpler. I kept the broker path because demonstrating message-driven design is one of the goals of this project. The sweep is still planned as the backstop.
- I rejected a library or CDC (Spring Modulith's event publication registry, Debezium) in place of a handwritten relay. It adds dependencies or infrastructure for roughly 150 lines of code that are easy to test.

## Tests
- `OutboxRelayTest`: each delivery outcome (ack, nack, confirm timeout, unroutable, send exception, broker-down halt, batch stall when nothing is delivered, retries ending in `FAILED`).
- `OutboxRelayConcurrencyTest`: two relays running at once send every message exactly once.
- `OutboxMessageRepositoryTest`: `SKIP LOCKED` gives two concurrent transactions disjoint batches.
- `OutboxIntegrationTest`: against real PostgreSQL and RabbitMQ, an event travels through the delay queue to the real listener; an unroutable message stays pending with its error recorded; with the broker frozen, `createHold` still succeeds and the hold expires once the broker is back.