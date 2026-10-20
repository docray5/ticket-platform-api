# ADR 03 RabbitMQ Delayed Queue (TTL + DLX) for Hold Expiry

## Context
When a user reserves seats (i.e. creates a hold), then that `Hold` must automatically expire and release its seats 5 minutes after creation if the customer doesn't confirm in time.

## Decision
The Hold expiry is event-driven via a queue level TTL + dead letter exchange using Rabbit MQ, rather than a job polling the `holds` table.

The expiry message is recorded through the transactional outbox (see [ADR 05](/docs/adrs/05-transactional-outbox.md)) in the same transaction as the hold, then published to `hold.expiry.delay.queue`. After the TTL RabbitMQ dead letters it into `hold.expiry.exchange`, which routes it to the real queue where `HoldMessageListener.handleHoldExpiry` consumes it.

I also avoided a trap: head of line blocking, which is caused by RabbitMQ only evaluating message's TTL when it reaches the head of the queue. If TTL were set per message with variable hold durations, a long TTL message queued ahead of a short TTL one would block the short one from expiring on time. The fix is to set TTL at the queue level (`x-message-ttl`) and keep the hold duration a single global constant (currently 5 minutes) rather than per showtime or per request. This makes it so that every message in the queue expires after the same fixed window regardless of position.

This behavior is tested here `HoldExpiryIntegrationTest.shouldReleaseSeatsAndExpireHold_WhenHoldTtlElapses`

## Consequences
Pros:
- No primary polling of the `holds` table. The relay only polls a small, partial-indexed set of pending rows, so its load doesn't scale with the number of active holds.
- Demonstrates message driven design, which is one of the things I wanted this project to showcase.
- Safe concurrent execution: When the consumer picks up the expiry message, it takes the exact same pessimistic lock on the Hold row described in [Adr 02](/docs/adrs/02-pessimistic-vs-optimistic-locking.md) before mutating anything. This shared lock is what makes it safe for an expiry message and a concurrent confirmation request to race against each other without corrupting the seat state.
- Async consumers drop the MDC logging context. To fix this, the correlationId is passed inside the HoldExpiryMessage itself. The listener manually puts it back into the MDC, meaning one ID links the original HTTP hold creation perfectly to its background expiration.

Negatives:
- The TTL starts when the relay publishes, not at commit, so expiry lags by the outbox poll interval (default is 1s), and after a broker outage seats can stay `HELD` for the outage duration plus TTL. However `confirmHold` still rejects the expired hold, so only seat availability is affected.
- Hold duration must be a single global constant in v1. The queue level TTL approach can't support a variable, per request hold duration without switching to the `rabbitmq_delayed_message_exchange` plugin (this is deliberately deferred as it adds a broker plugin dependency this project doesn't otherwise need).
- No independent backstop today if a hold expiry message is ever lost (broker crash between publish and delivery) or its outbox row ends up `FAILED`. There are scheduled cleanup jobs for the idempotency and outbox tables, but not yet a periodic reconciliation sweep for holds whose expiry time has passed with no corresponding expiry message ever landing.
- Infinite retry loops on consumer failure. If the `HoldMessageListener` encounters an unexpected error (like a database connection failure), it rethrows the exception. Because I haven't configured a retry backoff policy or a failure-specific DLQ for the consumer, Spring AMQP defaults to re-queuing the message instantly and infinitely. Handling failed message processing gracefully is a known gap for v1.

## Alternatives considered:
- A polling scheduler as the primary mechanism would be correct and simpler, since `expires_at` is the source of truth. I chose the event-driven path to showcase message-driven design. A reconciliation sweep is still planned as the backstop (see Negatives and [ADR 05](/docs/adrs/05-transactional-outbox.md)).
- `rabbitmq_delayed_message_exchange` plugin was deferred from v1, while it would allow per-hold variable TTLs, the project just doesn't need variable hold durations yet, and it adds a non-default broker plugin as a deployment dependency.