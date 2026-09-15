# ADR 03 RabbitMQ Delayed Queue (TTL + DLX) for Hold Expiry

## Context
When a user reserves seats (i.e. creates a hold), then that `Hold` must automatically expire and release its seats 5 minutes after creation if the customer doesn't confirm in time.

## Decision
The Hold expiry is event-driven via a queue level TTL + dead letter exchange using Rabbit MQ, rather than database polling job.

On `createHold`, once the DB transaction commits, a `HoldExpiryMessage` is published to `hold.expiry.delay.queue`. It sits there untouched until RabbitMQ's TTL dead letters it into `hold.expiry.exchange`, which routes it to the real queue where `HoldMessageListener.handleHoldExpiry` consumes it.

I also avoided a trap: head of line blocking, which is caused by RabbitMQ only evaluating message's TTL when it reaches the head of the queue. if TTL were set per message with variable hold durations, a long TTL message queued ahead of a short TTL one would block the short one from expiring on time. The fix is to set TTL at the queue level (`x-message-ttl`) and keep the hold duration a single global constant (currently 5 minutes) rather than per showtime or per request. This makes it so that every message in the queue expires after the same fixed window regardless of position.

This behavior is tested here `HoldExpiryIntegrationTest.shouldReleaseSeatsAndExpireHold_WhenHoldTtlElapses`

## Consequences
Pros:
- Expiry happens right when it's supposed to, bounded by RabbitMQ's own timing, not up to a full poll interval late.
- Efficiency: No continuous `SELECT ... WHERE expires_at < now()` load on Postgres.
- Demonstrates message driven design, which is one of the things I wanted this project to showcase.
- No ghost messages. Tying the message publication to the `AFTER_COMMIT` phase guarantees that if the primary hold transaction rolls back (e.g., due to a database constraint violation), the expiry message is never sent to RabbitMQ.
- Safe concurrent execution: When the consumer picks up the expiry message, it takes the exact same pessimistic lock on the Hold row described in [Adr 02](/docs/adrs/02-pessimistic-vs-optimistic-locking.md) before mutating anything. This shared lock is what makes it safe for an expiry message and a concurrent confirmation request to race against each other without corrupting the seat state.
- Async consumers drop the MDC logging context. To fix this, the correlationId is passed inside the HoldExpiryMessage itself. The listener manually puts it back into the MDC, meaning one ID links the original HTTP hold creation perfectly to its background expiration.

Negatives:
- Hold duration must be a single global constant in v1. The queue level TTL approach can't support a variable, per request hold duration without switching to the `rabbitmq_delayed_message_exchange` plugin (this is deliberately deferred as it adds a broker plugin dependency this project doesn't otherwise need).
- No independent backstop today if a hold expiry message is ever lost (broker crash between publish and delivery). There's a scheduled cleanup job for the idempotency table, but not yet a periodic reconciliation sweep for holds whose expiry time has passed with no corresponding expiry message ever landing.
- Infinite retry loops on consumer failure. If the `HoldMessageListener` encounters an unexpected error (like a database connection failure), it rethrows the exception. Because I haven't configured a retry backoff policy or a failure-specific DLQ for the consumer, Spring AMQP defaults to re-queuing the message instantly and infinitely. Handling failed message processing gracefully is a known gap for v1.

## Alternatives considered:
- Polling scheduler as the primary mechanism was rejected, as it adds continuous db load that scales with the number of active holds. (Although a reconciliation job running alongside this queue based primary path is planned for phase 2 - see negatives and [README](/README.MD))
- `rabbitmq_delayed_message_exchange` plugin was deferred from v1, while it would allow per-hold variable TTLs, the project just doesn't need variable hold durations yet, and it adds a non-default broker plugin as a deployment dependency.