[![Java CI with Maven](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/docray5/ticket-platform-api/actions/workflows/build-and-test.yml)

WIP

backend for a ticket booking platform built with spring

the goal is to make it as much in an industry standard way as possible.

## Tech Stack

| Layer                    | Choice                                                    |
|:-------------------------|:----------------------------------------------------------|
| **Language / Framework** | Java 21, Spring Boot 4.0.7                                |
| **Database**             | PostgreSQL + Flyway Migrations                            |
| **Messaging**            | RabbitMQ (soon)                                           |
| **Testing**              | Testcontainers, JUnit 5, Mockito                          |
| **Auth & API Docs**      | Spring Security, JWT (HS256), Swagger (springdoc-openapi) |

## Implemented so far:
- JWT auth for login
- Swagger docs
- Admin catalog (Create + read and safe showtime creation that checks for any overlapping showtime and bulk seat generation)
- RFC 7807 error handling

## How to run:
1. (**for local testing you don't need a .env file** as of right now, because a dev-only default secret is included in application.yml as a fallback when .env file does not exist, for convenience, real deployments must provide their own JWT_SECRET)
2. Prerequisites (Java 21, Docker)
3. `docker-compose up`
4. `./mvnw spring-boot:run`
5. and after starting the app, docs are available under this url: http://localhost:8080/swagger-ui/index.html

## Scope for the mvp:
- *Race conditions:* expiry vs paying and two people reserving the same seat at the same millisecond
- Seat reservation expiry with RabbitMQ
- mocked payments
- JWT auth
- *Idempotency:* preventing double bookings
- structure using the modular monolith paradigm
- users being able to browse events, see limited seats/tickets per event, hold a ticket during checkout (with expiry), and complete payment
- documentation using swagger

Known limitations:
- secrets are managed via local spring variables for local development but for production AWS deployment or other tools would be used
- No Admin Update/Delete
- No refunds
- HS256 vs. RS256: JWTs are signed symmetrically (HS256). This is fine for a monolith, but I would upgrade this to asymmetric (RS256) if were to split this into multiple microservices
- no rate limiting

Docs:
- [Api Endpoints](/docs/api-endpoints.md) (or rather an implementation plan for endpoints) (also swagger)
- [Architecture](/docs/architecture.md)
- [Adr 01: Modular monolith vs microservices](/docs/adrs/01-modular-monolith-vs-microservices.md)

The first ever sketch of the database: (the actual db is a bit different already)
![initial db scheme](/docs/initial-db-scheme.png)

credits to https://dbdiagram.io/ for visualization