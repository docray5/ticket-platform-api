WIP

backend for a ticket booking platform built with spring

the goal is to make it as much in an industry standard way as possible.

Scope for the mvp:
- *Race conditions:* expiry vs paying and two people reserving the same seat at the same millisecond
- Seat reservation expiry with RabbitMQ
- mocked payments
- JWT auth
- *Idempotency:* preventing double bookings
- structure using the modular monolith paradigm
- users being able to browse events, see limited seats/tickets per event, hold a ticket during checkout (with expiry), and complete payment
- documentation using swagger

The first sketch of the database: (subject to change)
![initial db scheme](/docs/initial-db-scheme.png)

credits to https://dbdiagram.io/ for visualization