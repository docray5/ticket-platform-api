# ADR 01 Architecture pattern

Note: this decision was made like 2026-07-15, before the repo on GitHub was made, during the project planning phase. But the acutal ADR write date is 2026-08-05.

## Context
I am building a rather complex backend api that requires proper architecture which supports distinct business domains
(like user, catalog, booking, etc. functionality), and a one that won't be an overkill for this complex yet not that large project.

## Decision
I have decided to go for modular monolith - A single deployable Spring Boot application that is internally organized into distinct modules mirroring bounded contexts.

## Consequences
Pros:
- **Future-proof** - I can move it to easily microservices if the project grows, thanks to module boundaries
- **Simpler cross-module interactions** than in microservices - Because everything runs in one application, I can wrap cross-module operations (like locking a seat row in `catalog` and writing a hold record in `booking`) in a single database transaction.
In microservices that would be harder to set up, since we need to make an HTTP Rest call between two modules to confirm they have both succeeded. 
- **Developer velocity** - I can run this in two commands (docker and spring). And I can spend more time on other features.
- **Zero Network Overhead:** - there are no cross-domain calls.

Negatives:
- **Coupled Scaling** - The entire application must be scaled vertically or horizontally as one unit, rather than independently scaling just the heavy-traffic `booking` domain. However, thanks to the module boundaries, splitting this into real microservices later would be a mechanical extraction rather than a full rewrite.
- **Shared Database** - In microservices each module has its own database and this has it's strong sides. While the code is modularized, here I rely on a shared database.

## Alternatives considered:
- Regular monolith - With this one the code is at high risk of tight coupling, or to put it simply: it will become a spaghetti too quickly.
- Microservices - While this provides ultimate physical decoupling, it was rejected because of too much overhead for solo dev. I wouldn't benefit from the modularity - like a team of a few people for example.