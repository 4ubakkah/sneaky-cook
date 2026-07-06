# Recipe API

REST API for managing and searching favourite recipes. Contract-first OpenAPI
spec, clean architecture (domain → application → infrastructure → api), and
PostgreSQL full-text search on instructions.

> **Note:** the implementation lives on the `initial-implementation` branch,
> kept separate from `main` pending review.

## Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**
- **Docker** — required for E2E and specification-tier tests (Testcontainers
  spins up PostgreSQL automatically). Also used for the one-command local stack.

After cloning, generate sources before the IDE can compile:

```bash
mvn generate-sources
```

Generated OpenAPI interfaces and MapStruct mappers live under each module's
`target/` directory and are **never committed**.

## Quick start (Docker)

```bash
docker compose up --build
```

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Swagger UI (renders `recipe-api.yaml`) |
| http://localhost:8080/openapi/recipe-api.yaml | Raw OpenAPI contract |
| http://localhost:8080/actuator/health | Liveness/readiness probe |
| http://localhost:8080/actuator/metrics | Prometheus-style metrics |

Scale-out smoke check (stateless API tier):

```bash
docker compose up --build --scale api=3
```

## Quick start (local JVM)

Start PostgreSQL only:

```bash
docker compose up postgres -d
```

Run the API with dev profile (SQL logging on):

```bash
mvn -pl api spring-boot:run -Dspring-boot.run.profiles=dev
```

Production-style run (credentials from environment):

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/recipes
export DB_USERNAME=recipes
export DB_PASSWORD=recipes
mvn -pl api spring-boot:run
```

### Example requests

All recipe endpoints require a JWT bearer token; recipes belong to the user
who created them and are invisible to everyone else. Register once, log in,
and send the token on every request:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username": "alice", "password": "correct-horse-battery"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "alice", "password": "correct-horse-battery"}' | jq -r .accessToken)
```

Create a recipe:

```bash
curl -s -X POST http://localhost:8080/api/v1/recipes \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Potato gratin",
    "vegetarian": true,
    "servings": 4,
    "ingredients": ["potatoes", "cream", "cheese"],
    "instructions": "Layer and bake in the oven until golden."
  }'
```

Combined filter example (five criteria composed in one request):

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/recipes?vegetarian=true&servings=4&includeIngredients=potatoes&excludeIngredients=salmon&instructionsContain=oven'
```

The signing secret comes from `JWT_SECRET` (a development default ships in
`application.yaml`; the `prod` profile has no default and fails fast without
the variable). Tokens are HS256, valid for one hour, subject = user id.

## Module map

| Module | Role |
|--------|------|
| `domain` | Recipe aggregate, filter/sort value objects, repository port — zero framework imports |
| `application` | Spring-free use cases orchestrating the port |
| `infrastructure` | JPA adapter, Flyway migrations, full-text Specifications |
| `api` | HTTP edge: generated OpenAPI interfaces, controllers, Swagger UI, exception handling |

## Running tests

Default build (excludes `@Tag("red")` tests, runs JaCoCo gate on domain/application):

```bash
mvn test
```

| Tier | Command | Scope |
|------|---------|-------|
| All modules | `mvn test` | Domain + application unit, infrastructure `@DataJpaTest`, API E2E + ArchUnit |
| Full E2E (incl. red) | `mvn -Pe2e-all test` | Entire E2E suite including not-yet-implemented features |
| Mutation testing | `mvn -Pmutation test` | PIT on `domain` + `application` (≥ 75% mutants killed) |

JaCoCo reports: `domain/target/site/jacoco/index.html`,
`application/target/site/jacoco/index.html`.

**Docker must be running** for `api` and `infrastructure` test modules.

**PIT note:** the mutation profile requires a supported JDK (Java 21–23 verified;
Java 26 may fail during coverage minion startup).

## API documentation

Swagger UI is served from the `swagger-ui` webjar via a static page at
`/swagger-ui.html` — a deliberate deviation from springdoc (the hand-written
`api/src/main/resources/openapi/recipe-api.yaml` is the single source of truth;
no code scanning).

## Bruno collections

Two [Bruno](https://www.usebruno.com/) collections live in `bruno/` (plain-text,
version-controlled — open the folder in the Bruno app, or run headless via the CLI):

- **`bruno/recipe-api`** — exploration collection. Run *Auth → Register* once,
  then *Auth → Login* (it stores the JWT in the `token` environment variable);
  every other request inherits the bearer token from the collection.
- **`bruno/recipe-api-tests`** — assertion suite mirroring the E2E tests at the
  HTTP level: auth flows and error contracts, CRUD with field-level checks,
  every filter criterion (including the combined-filter scenario),
  ownership isolation, and the open/secured actuator split. It registers
  fresh, timestamped users each run, so it is repeatable against a running stack:

```bash
cd bruno/recipe-api-tests
npx @usebruno/cli run --env local            # against docker compose (port 8080)
npx @usebruno/cli run --env local --env-var baseUrl=http://localhost:9999  # custom target
```

## Next steps / possible improvements

See `docs/plans/2026-07-03/recipe-api-design-specification.md` §11. Highlights:

- Auth hardening: RS256 + JWKS instead of the shared HS256 secret, refresh
  tokens, a real password policy, and login throttling.
- CI pipeline (build, all test tiers, JaCoCo report, image publish).
- Structured ingredients with quantities/units and a canonical ingredient table.
- Multilingual full-text search (language column, per-language index).
- Response caching on the search endpoint; read replicas if write load grows.
- PATCH for partial updates; optimistic locking via `@Version`.
- Rate limiting at the edge; OpenTelemetry tracing.
