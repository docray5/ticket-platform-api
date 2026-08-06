# Package structure (Modular Monolith)
The application is a single deployable Spring Boot artifact, internally organized into strict modules that mirror bounded contexts. This avoids the infrastructure overhead of distributed microservices while keeping the codebase strictly decoupled.

- `catalog`: Venues, Halls, Movies, Showtimes, and the seat map create/read model.
- `booking`: Holds, Bookings, seat state transitions, and idempotency mechanics.
- `payment`: Mock payment processing.
- `notification`: Async consumers for post-booking events.
- `user`: Registration, authentication, and JWT issuance.
- `common`: (ProblemDetail mapping, Correlation IDs).
- `config`: Security config, RabbitMQ topology, and OpenAPI setup.
- A module never imports another module's repository or entity classes directly. Cross-module access must route through a public interface (Facade) exposed at the root of the owning package.

# Security

## Auth strategy: Stateless JWT signed symmetrically using HS256
- `POST /auth/register` - validates input, BCrypt-hashes the password
- `POST /auth/login` - verifies credentials, issues a self-signed HS256 JWT with claims `sub` (userId), `role`, `iat`, `exp`
- A `JwtAuthenticationFilter`, registered ahead of Spring Security's default filters, reads `Authorization: Bearer <token>`, validates signature + expiry, and populates the `SecurityContext` with a custom `Authentication` carrying the userId and role
- Controllers retrieve the current user via `@AuthenticationPrincipal`
- Admin endpoints are gated with `@PreAuthorize("hasRole('ADMIN')")`

## Role based access control
- `CUSTOMER` — default on registration
- `ADMIN` — seeded manually, gates catalog-management endpoints

## Deliberately simplified for v1
- No refresh tokens - short-lived access token only, re-login on expiry
- No OAuth2
- HS256 (shared secret) instead of RS256 (asymmetric)
- No rate limiting on login/register in v1
- No password reset flow
- CSRF disabled - since this is a stateless, token-authenticated API with no browser session/cookie auth, so CSRF (which protects session-cookie-based auth) doesn't apply

# Error handling (built using the RFC 7807 standard)
- Domain Exceptions
  - All business logic errors inherit from an abstract `DomainException` base class, which maps directly to specific HTTP statuses
- Global Controller Advice
  - A single `@RestControllerAdvice` in the `common` package translates exceptions into structured `ProblemDetail` payloads
- Security Exceptions
  - `401 Unauthorized` and `403 Forbidden` errors triggered by Spring Security are intercepted by custom `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` components to ensure they also return standard RFC 7807 JSON.
- Logging vs. response
  - Client Visibility:
    - The client receives a safe, actionable detail message (e.g., "Seats s-A1 are already held") along with a unique `correlationId`. 5xx errors never leak internals like SQL statements or entity field names.
  - Server Visibility:
    - The server logs the full stack trace alongside the same correlationId to trace the failure path

Example:
```json
{
  "detail": "You do not have permission to access this resource.",
  "instance": "/api/v1/admin/venues",
  "status": 403,
  "title": "Access Denied",
  "type": "https://api.ticketing.dev/errors/access-denied",
  "correlationId": "68e18f24-a995-4d90-9e8a-7ba5dd04f66e"
}
```