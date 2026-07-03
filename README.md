# Recipe API

REST API for managing and searching favourite recipes. Contract-first OpenAPI
spec, clean architecture (domain → application → infrastructure → api), and
PostgreSQL full-text search on instructions.

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

Create a recipe:

```bash
curl -s -X POST http://localhost:8080/api/v1/recipes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Potato gratin",
    "vegetarian": true,
    "servings": 4,
    "ingredients": ["potatoes", "cream", "cheese"],
    "instructions": "Layer and bake in the oven until golden."
  }'
```

Combined filter from the assignment objective:

```bash
curl -s 'http://localhost:8080/api/v1/recipes?vegetarian=true&servings=4&includeIngredients=potatoes&excludeIngredients=salmon&instructionsContain=oven'
```

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

## Next steps

See `docs/plans/2026-07-03/recipe-api-design-specification.md` §11 — authentication
and ownership (build-order step 9) is designed but not yet implemented.
