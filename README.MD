WIP

backend for a ticket booking platform built with spring

the goal is to make it as much in an industry standard way as possible.

Implemented so far:
- JWT auth for login
- Swagger docs

Scope for the mvp:
- *Race conditions:* expiry vs paying and two people reserving the same seat at the same millisecond
- Seat reservation expiry with RabbitMQ
- mocked payments
- JWT auth
- *Idempotency:* preventing double bookings
- structure using the modular monolith paradigm
- users being able to browse events, see limited seats/tickets per event, hold a ticket during checkout (with expiry), and complete payment
- documentation using swagger

Known limitations:
- secrets are managed via local spring variables for local development but for production AWS deployment or other tools would be used.

Docs:
- [Api Endpoints](/docs/api-endpoints.md) (or rather an implementation plan for endpoints) (also swagger)

The first sketch of the database: (subject to change)
![initial db scheme](/docs/initial-db-scheme.png)

credits to https://dbdiagram.io/ for visualization