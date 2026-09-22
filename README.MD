[![Java CI with Maven](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml)
[![API Status](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fticket-platform-api.onrender.com%2Factuator%2Fhealth&query=%24.status&label=API%20Status&color=brightgreen&logo=render)](https://ticket-platform-api.onrender.com/actuator/health)

A backend for booking tickets, built to survive the messy stuff like: high traffic race conditions, flaky payments, and users who double-click everything. Spring Boot, PostgreSQL, and RabbitMQ underneath.

This is a modular monolith built to demonstrate concurrent data contention, idempotent financial operations, and reliable async messaging in a stateless API.

## Live demo and deployment
- API: https://ticket-platform-api.onrender.com/actuator/health
- Swagger docs: https://ticket-platform-api.onrender.com/swagger-ui/index.html
- Frontend demo to interact with the API: https://ticket-booking-front.bilioften.workers.dev/
  - Built with Next.js and React.
  - Login for test account if you don't want to create one: `test@gmail.com` with pass: `qwerty123`

## Tech Stack

| Layer                    | Choice                                                    |
|:-------------------------|:----------------------------------------------------------|
| **Language / Framework** | Java 21, Spring Boot 4.0.7                                |
| **Database**             | PostgreSQL + Flyway Migrations                            |
| **Messaging**            | RabbitMQ (TTL + DLX pattern)                              |
| **Testing**              | Testcontainers, JUnit 5, Mockito                          |
| **Auth & API Docs**      | Spring Security, JWT (HS256), Swagger (springdoc-openapi) |

## Docs:
- [Architecture explanation, with request flow for seat reservation](/docs/architecture.md)
- [ADR 01: Modular monolith vs microservices](/docs/adrs/01-modular-monolith-vs-microservices.md)
- [ADR 02: Locking and race conditions](/docs/adrs/02-pessimistic-vs-optimistic-locking.md)
- [ADR 03: Event driven expiry with RabbitMQ](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md)
- [ADR 04: Idempotency strategy](/docs/adrs/04-idempotency-key-strategy.md)
- [Api Endpoints](/docs/api-endpoints.md) (alternative to swagger)
- [Db schema](/docs/db-schema.png) (also below)

## Features and capabilities:

**Race conditions and concurrency defenses**
- Pessimistic row locking for seat reservation. It solves the classic "a lot of people click reserve on the last seat" problem, which resolves to exactly one winner, every time. It is also deadlock free.
- Apart from the point above, I have also implemented optimistic locking as a second line of defense on hold state transitions.
- Confirm vs Expire race is handled explicitly: if a hold's expiry message arrives at the same millisecond a customer confirms payment, both of them fight for the same row lock and whichever wins transitions the state, the other fails cleanly with no corrupted seat data. This is also covered by a multithreaded test.
- Seat state machine (AVAILABLE -> HELD -> BOOKED), reverting atomically on cancel or expiry.
- More info on the row lock: [ADR-02](/docs/adrs/02-pessimistic-vs-optimistic-locking.md) 

**Idempotency**
- Every hold creation and booking confirmation request requires an `Idempotency-Key` header. Retried requests (same key, same body) replay the exact original response instead of re-running any business logic, so if a user with bad internet connection spams the pay button like 5 times, they get charged only once.
- The filter hashes request bodies of these requests, so reusing a key with a different body is rejected outright instead of silently doing the wrong thing.
- Concurrent duplicate requests (same key, still in flight) are caught and cleanly rejected via db unique constraints rather than racing each other into two bookings.
- See: [ADR-04](/docs/adrs/04-idempotency-key-strategy.md)

**Async messaging**
- Hold expiry runs on RabbitMQ's queue-level TTL and a dead-letter exchange rather than database polling, this avoids the classic per-message-TTL head-of-line-blocking trap.
- Messages are only published after the database transaction actually commits, so a rolled-back request never produces a ghost RabbitMQ message.
- Async, event-driven booking confirmations, the listener lives in booking, delegating the actual notification work to a separate notification module
- Correlation IDs follow a request across the async boundary. A hold created via HTTP and expired seconds later by a queue consumer show up under the same ID in the logs.
- See: [ADR-03](/docs/adrs/03-delayed-queue-vs-polling-for-hold-expiry.md)

**Everything else**
- Modular monolith architecture: modules are strictly separated and no module reaches into another's repositories or entities directly, only through a public facade. (currently only a convention as I haven't implemented arch unit tests yet)
- JWT auth (HS256) with role-based access control
- Admin catalog management — venues, halls, movies, showtimes (with overlap checking, so you can't double-book a hall), bulk seat generation
- Mock payment processing with a simulated 10% decline rate
- Structured, RFC 7807–compliant error responses across the whole API, with a correlation ID on every error for tracing
- Interactive API docs via Swagger
- Payment runs in its own independent transaction, so a declined payment is permanently audited even when the parent booking transaction rolls back.
- A concurrency test suite that races real threads against real PostgreSQL and RabbitMQ instances: last-seat contention, confirm-vs-expire, cancel-vs-expire, duplicate idempotent requests - asserting the exact outcome each time
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

| Variable               | Default                                                           |
|------------------------|-------------------------------------------------------------------|
| `DB_URL`               | `jdbc:postgresql://localhost:5432/ticket_booking_db`              |
| `DB_USERNAME`          | `admin`                                                           |
| `DB_PASSWORD`          | `admin`                                                           |
| `RABBITMQ_URL`         | `localhost`                                                       |
| `RABBITMQ_PORT`        | `5672`                                                            |
| `RABBITMQ_VHOST`       | `/`                                                               |
| `RABBITMQ_SSL`         | `false`                                                           |
| `RABBITMQ_USER`        | `guest`                                                           |
| `RABBITMQ_PASS`        | `guest`                                                           |
| `JWT_SECRET`           | dev fallback is set in the `application.yml`                      |
| `PAYMENT_DECLINE_RATE` | `0.1`                                                             |
| `APP_ERROR_BASE_URI`   | `https://api.ticketing.dev/errors/`                               |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000`  (more origins can be separated by comma) |

## Roadmap:

**MVP - Completed, see features above**

**Phase 2**
- Backstop reconciliation job for orphaned holds, in case a RabbitMQ expiry message is ever lost
- Admin update/delete endpoints, and a real way to seed an admin account
- Refresh tokens, OAuth2, password reset
- Rate limiting on auth endpoints
- ArchUnit test enforcing module boundaries in CI, not just convention
- Metrics
- Outbox pattern

**Phase 3 - if the project outgrows a monolith**
- Gatling/k6 test to test api under N load
- Migrate to microservices - thanks to modular monolith it will require less friction, although the synchronous pessimistic locking model would need to be replaced with distributed locks (e.g., Redis)
- Swap HS256 for RS256 once there's more than one service verifying tokens
- Move idempotency-key storage from Postgres to Redis if key volume ever becomes a bottleneck

## Known limitations:
- No Update/Delete for admin endpoints, and no seeded way to create an Admin account - currently needs a manual DB edit
- No refunds
- HS256 vs. RS256: JWTs are signed symmetrically (HS256). This is fine for a monolith, but I would upgrade this to asymmetric (RS256) if were to split this into microservices
- No rate limiting
- A payment decline is cached against its Idempotency-Key just like a success is, so retrying with the same key after a `402` replays the same decline forever. The client needs a fresh key to actually retry payment.
- Client side retry risk, if the user successfully pays but their network drops before receiving the response, front end might generate a new idempotency key on retry (if implemented that way). Then, if the user clicks pay again, it bypasses the idempotency lock and double charges them. So proper client side integration requires caching the key locally per logical attempt.
- Payment processing is mocked with a simulated 10% decline rate
- No independent backstop job for holds yet: if a hold-expiry RabbitMQ message is ever lost, there's currently no periodic sweep that would catch and release the orphaned hold (there is one for the idempotency table, not yet for holds).
- No refresh tokens, no OAuth2, no password reset flow, there is short-lived access token only, re-login on expiry.
- Connection Pool Starvation Deadlock inside `confirmHold` calling `pay` with its own transaction
- As of right now you can book and browse showtimes from the past for live demo purposes

Current DB schema:
![db schema](/docs/db-schema.png)

credits:
- https://dbdiagram.io/ for visualization of the [initial db schema](/docs/initial-db-scheme.png), and to DBeaver for the visualization of the current db schema
- https://render.com/ for DB and the API hosting
- https://www.cloudamqp.com/ for message broker hosting
- https://www.cloudflare.com/ for hosting front end.
