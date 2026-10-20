[![Java CI with Maven](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml)
[![API Status](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fticket-platform-api.onrender.com%2Factuator%2Fhealth&query=%24.status&label=API%20Status&color=brightgreen&logo=render)](https://ticket-platform-api.onrender.com/actuator/health)

A backend for a ticket booking platform, built to survive the messy stuff like: high traffic race conditions, flaky payments, and users who double click everything. Spring Boot, PostgreSQL, and RabbitMQ underneath.

This is a modular monolith built to demonstrate concurrent data contention, idempotent financial operations, and reliable async messaging in a stateless API.

## Live demo and deployment
- API: https://ticket-platform-api.onrender.com/actuator/health
- Swagger docs: https://ticket-platform-api.onrender.com/swagger-ui/index.html
- Frontend demo to interact with the API: https://ticket-booking-front.bilioften.workers.dev/
  - Built with Next.js and React.
  - Login for test account if you don't want to create one: `test@gmail.com` with pass: `qwerty123`

## Tech Stack

| Layer                | Choice                                                    |
|:---------------------|:----------------------------------------------------------|
| Language / Framework | Java 21, Spring Boot 4.0.7                                |
| Database             | PostgreSQL + Flyway Migrations                            |
| Messaging            | RabbitMQ (TTL + DLX, transactional outbox)                |
| Testing              | Testcontainers, JUnit 5, Mockito                          |
| Auth and API Docs    | Spring Security, JWT (HS256), Swagger (springdoc-openapi) |

## Docs:
- [Architecture explanation, with request flow for seat reservation](/docs/architecture.md)
- [ADR 01: Modular monolith vs microservices](/docs/adrs/01-modular-monolith-vs-microservices.md)
- [ADR 02: Locking and race conditions](/docs/adrs/02-pessimistic-vs-optimistic-locking.md)
- [ADR 03: Event driven expiry with RabbitMQ](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md)
- [ADR 04: Idempotency strategy](/docs/adrs/04-idempotency-key-strategy.md)
- [ADR 05: Transactional outbox for reliable message publishing](/docs/adrs/05-transactional-outbox.md)
- [Api Endpoints](/docs/api-endpoints.md) (alternative to swagger)
- [Db schema](/docs/db-schema.png) (also below)

## Features and capabilities:

**Race conditions and concurrency defenses**
- Pessimistic row locking for seat reservation. It solves the classic "a lot of people click reserve on the last seat" problem, which assures exactly one person will get it. And also avoids lock order deadlocks by design.
- Apart from the point above, I have also implemented optimistic locking as a second line of defense on hold state transitions.
- Confirm vs Expire race is handled explicitly: if a hold's expiry message arrives at the same millisecond a customer confirms payment, both of them fight for the same row lock and whichever wins transitions the state, the other fails cleanly with no corrupted seat data. This also has a multithreaded test.
- Seat state machine (AVAILABLE -> HELD -> BOOKED), reverting atomically on cancel or expiry.
- More info on the row lock: [ADR-02](/docs/adrs/02-pessimistic-vs-optimistic-locking.md) 

**Idempotency**
- Every hold creation and booking confirmation request requires an `Idempotency-Key` header. Retried requests (same key, same body) replay the exact original response (for any non 5xx outcome) instead of rerunning any business logic, so if a user with bad internet connection spams the pay button like 5 times, they are not charged twice for the same request.
- The filter hashes request bodies of these requests, so reusing a key with a different body is rejected outright instead of silently doing the wrong thing.
- Concurrent duplicate requests (same key, still in flight) are caught and cleanly rejected via db unique constraints rather than racing each other into two bookings.
- See: [ADR-04](/docs/adrs/04-idempotency-key-strategy.md)

**Async messaging**
- Transactional outbox: events are saved to a database table in the same transaction as the change that caused them, and a background relay publishes them to RabbitMQ. If the broker is unreachable, the relay just waits and publishes once it's back online, without burning retries.
- Hold expiry runs on RabbitMQ's queue level TTL and a dead letter exchange.
- Async, event driven booking confirmations, the listener lives in booking, delegating the actual notification work to a separate notification module
- Correlation IDs follow a request across the async boundary. A hold created via HTTP and expired seconds later by a queue consumer show up under the same ID in the logs.
- See: [ADR-03](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md) and [ADR-05](/docs/adrs/05-transactional-outbox.md) 

**Everything else**
- Modular monolith architecture: modules are kept separate by convention.
- JWT auth (HS256) with role based access control
- Admin catalog management: venues, halls, movies, showtimes (with overlap checking, so you can't double book a hall), bulk seat generation
- Mock payment processing with a simulated 10% decline rate
- Error responses follow RFC 7807 across the whole API, with a correlation ID on every error for tracing.
- Interactive API docs via Swagger
- Payment runs in its own independent transaction, so a declined payment is permanently audited even when the parent booking transaction rolls back.
- A concurrency test suite that races threads against PostgreSQL and RabbitMQ test containers: last seat contention, confirm vs expire, cancel vs expire, duplicate idempotent requests, outbox concurrency and pause/unpause resilience
- Automatic deployment to https://render.com/ after merging PRs to main branch.
- more info in the ADRs

## How to run:
1. Prerequisites (Java 21, Docker)
2. for anything beyond pure local dev: `cp .env.example .env` (application.yml has dev only fallbacks)
3. `docker compose up -d`
4. `./mvnw spring-boot:run`
5. and after starting the app, docs are available under this url: http://localhost:8080/swagger-ui/index.html
- to run tests: `./mvnw test` (needs docker running)
- you can also run the spring app as a docker container with: `docker compose -f docker-compose.app.yml up --build`

Config overview (dev fallbacks in the application.yml):

| Variable                              | Default                                                           |
|---------------------------------------|-------------------------------------------------------------------|
| `DB_URL`                              | `jdbc:postgresql://localhost:5432/ticket_booking_db`              |
| `DB_USERNAME`                         | `admin`                                                           |
| `DB_PASSWORD`                         | `admin`                                                           |
| `RABBITMQ_URL`                        | `localhost`                                                       |
| `RABBITMQ_PORT`                       | `5672`                                                            |
| `RABBITMQ_VHOST`                      | `/`                                                               |
| `RABBITMQ_SSL`                        | `false`                                                           |
| `RABBITMQ_USER`                       | `guest`                                                           |
| `RABBITMQ_PASS`                       | `guest`                                                           |
| `JWT_SECRET`                          | dev fallback is set in the `application.yml`                      |
| `PAYMENT_DECLINE_RATE`                | `0.1`                                                             |
| `APP_ERROR_BASE_URI`                  | `https://api.ticketing.dev/errors/`                               |
| `CORS_ALLOWED_ORIGINS`                | `http://localhost:3000`  (more origins can be separated by comma) |
| `OUTBOX_POLL_INTERVAL`                | `PT1S`                                                            |
| `OUTBOX_CONFIRM_TIMEOUT`              | `PT3S`                                                            |
| `OUTBOX_PROCESSED_MESSAGES_RETENTION` | `7d`                                                              |
| `MAX_CONNECTION_POOL_SIZE`            | `10`                                                              |

## Roadmap:

**MVP Completed, see features above**

**Phase 2**
- Admin update/delete endpoints, and a real way to seed an admin account
- Refresh tokens, OAuth2, password reset
- Rate limiting on auth endpoints
- ArchUnit test enforcing module boundaries in CI, not just convention
- Backstop reconciliation job that guarantees hold expiry (outbox + RabbitMQ is the fast path)
- Metrics, pending/failed outbox row count
- Retry with backoff and a dead-letter queue for failed message consumers

**Phase 3 - if the project outgrows a monolith**
- Gatling/k6 test to test api under N load
- Migrate to microservices, and thanks to modular monolith it will require less friction, although the synchronous pessimistic locking model would need to be replaced with distributed locks (e.g., Redis)
- Swap HS256 for RS256 once there's more than one service verifying tokens
- Move `idempotency-key` storage from Postgres to Redis if key volume ever becomes a bottleneck

## Known limitations:
- No Update/Delete for admin endpoints, and no seeded way to create an Admin account, it currently needs a manual DB edit
- No refunds or cancellation of confirmed bookings 
- No rate limiting
- A payment decline is cached against its `Idempotency-Key` just like a success is, so retrying with the same key after a `402` replays the same decline forever. The client needs a fresh key to actually retry payment.
- Client side retry risk, if the user successfully pays but their network drops before receiving the response, front end might generate a new idempotency key on retry (if implemented that way). Then, if the user clicks pay again, it bypasses the idempotency lock and double charges them. So proper client side integration requires caching the key locally per logical attempt.
- No refresh tokens, no OAuth2, no password reset flow, there is short lived access token only, re-login on expiry.
- Users can book and browse showtimes from the past, it's implemented this way for live demo purposes
- Outbox rows that fail delivery repeatedly (broker nacks, unroutable messages, or a broker that hangs and never confirms for roughly 2-3 minutes with the default retry settings) transition to `FAILED`. A broker that is simply unreachable never causes this, because the relay waits without consuming retries. Because a reconciliation sweep job is not yet implemented, seats tied to permanently failed expiry messages remain held until manually cleared. (see [ADR-05](/docs/adrs/05-transactional-outbox.md))
- Message delivery is at least once, so duplicates can happen, for example if the app crashes after the broker confirms but before the database commit. Hold expiry handles that fine because it rechecks the hold's status under a row lock. The booking confirmation notification doesn't deduplicate yet, since it's only a mock, but the outbox message id is available for that.
- Holds created while RabbitMQ is down get their expiry timer only once the relay manages to publish the message, so their seats can stay `HELD` for up to the outage duration beyond their expiry time. `confirmHold` still rejects the expired hold, so only seat availability is affected until the reconciliation sweep exists.
- An unexpected error in a message consumer (for example a database failure while expiring a hold) requeues the message immediately and indefinitely. There is no backoff or dead-letter queue for consumers yet (see [ADR-03](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md)).
- Payment processing is mocked with a simulated 10% decline rate.
- Payment and booking are not atomic. Payment runs in its own transaction and commits before `confirmHold` finishes, so if `confirmHold` then fails (a database error at commit, for example), you can end up with a successful payment and no booking. That `5xx` also removes the idempotency key, so a retry can charge again. There's no automatic refund or void step. This is harmless because payments are mocked, but a real payment gateway would need a compensating refund, or an idempotency key sent to the provider for each payment attempt.
- Related to the point above there is a connection pool starvation issue inside `confirmHold` calling `pay` with its own transaction, although it's not really a problem as of right now since payment module is mocked and executes almost instantly.

Current DB schema:
![db schema](/docs/db-schema.png)

credits:
- https://dbdiagram.io/ for visualization of the [initial db schema](/docs/initial-db-scheme.png), and to DBeaver for the visualization of the current db schema
- https://render.com/ for DB and the API hosting
- https://www.cloudamqp.com/ for message broker hosting
- https://www.cloudflare.com/ for hosting front end.
