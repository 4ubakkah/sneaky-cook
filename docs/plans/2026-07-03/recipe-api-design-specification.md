# Recipe API — Design Solution Specification

Revision history:

| Rev | Change |
|---|---|
| 1 | Baseline design (single-module layered service, `LIKE` search, 6–8 h budget) |
| 2 | Quality drivers added: scalability, reusability, non-repetitive code, purposeful tests → multi-module clean architecture, full-text search, MapStruct, ArchUnit, JaCoCo, PIT; budget stretched |
| 3 | Process change: contract-first OpenAPI + code generation, then TDD E2E suite, then implementation |
| 4 | Test data rules (realistic fixtures, field-level assertions) and worked request/response examples |
| 5 | Consistency pass: budget reconciled, cut-path corrected, ambiguities removed |
| 6 | Requirement register (REQ-1…REQ-16) with code-reference convention (§12) |

## 1. Context and goals

A standalone REST service for managing favourite recipes: create, update, delete,
fetch, and filter. Filters are combinable in a single request: vegetarian status,
number of servings, ingredients to include, ingredients to exclude, and free-text
search within the cooking instructions.

Success criteria:

- All CRUD and filter operations work through a documented REST API.
- Data is persisted in PostgreSQL; instruction search is index-backed full-text,
  not `LIKE` scans.
- The service is stateless and scales horizontally with no code change.
- Clean architecture with enforced boundaries: framework-free domain and
  application modules, verified by ArchUnit tests, reusable outside this service.
- No repetitive code: mapping is generated (MapStruct), fixtures are shared.
- Tests provably serve their purpose: JaCoCo coverage gate + PIT mutation
  testing on the core modules.
- The whole system starts with one command (`docker compose up`).
- Implementation targets 10–12 hours against a ~13 h full-scope estimate; the
  cut path in §10 reconciles the difference, and anything beyond goes to
  "Next steps".

Assumptions (design breaks noted in §8):

- Single user, no authentication.
- Recipe volume up to millions is handled by the chosen indexes; traffic scaling
  is horizontal (stateless service), storage scaling is vertical Postgres first.
- "Remove" is a hard delete.

Delivery constraint: the delivered repository — code, docs, commit history —
must contain no reference to the assignment's origin. The original assignment
text file stays out of version control (`.gitignore`).

## 2. Chosen stack

| Concern | Technology | Rationale |
|---|---|---|
| Language / framework | Java 21, Spring Boot 3.x | Current LTS; records and pattern matching reduce boilerplate |
| Build | Maven, multi-module | Enforces dependency direction at compile time |
| Persistence | Spring Data JPA (Hibernate) + PostgreSQL 16 | JPA Specifications give composable dynamic filters |
| Instruction search | Postgres full-text: generated `tsvector` column + GIN index | Index-backed, ranked search; scales where `LIKE '%…%'` cannot |
| Migrations | Flyway | Versioned schema, runs on startup |
| DTO mapping | MapStruct | Compile-time generated mapping — zero repetitive hand mapping |
| Validation | Jakarta Bean Validation | Constraints declared once in the OpenAPI contract, emitted onto generated DTOs |
| Error format | RFC 7807 `ProblemDetail` | Native in Spring Boot 3, consistent error contract |
| API contract | Contract-first OpenAPI 3.0 YAML + openapi-generator (`spring` generator) | Hand-written contract is the source of truth; server interfaces and DTOs are generated, so code cannot drift from docs |
| Tests | JUnit 5, Mockito, REST Assured, Testcontainers (Postgres) | E2E tests written first (TDD); integration tests against the real database engine |
| Architecture tests | ArchUnit | Module and dependency rules enforced as failing tests, not conventions |
| Test quality | JaCoCo coverage gate + PIT mutation testing | Proves tests assert behaviour, not just execute lines |
| Observability | Spring Boot Actuator (health, info, metrics) | Health probes for orchestration and scale-out |
| Packaging | Multi-stage Dockerfile + docker-compose | One-command start for API + database |

## 3. Component design — Maven multi-module clean architecture

Four modules; dependencies point strictly inward and are enforced twice — by
Maven (a module cannot see what it doesn't declare) and by ArchUnit tests:

```
recipe-api (parent pom)
├── domain           # pure Java: Recipe, RecipeFilter, RecipeRepository port,
│                    # invariants. No Spring, no JPA, no annotations.
├── application      # use cases: CreateRecipe, UpdateRecipe, DeleteRecipe,
│                    # GetRecipe, SearchRecipes. Depends only on domain.
│                    # Spring-free (plain constructors, wired by the api module).
├── infrastructure   # JPA entities, Specifications, repository adapter
│                    # implementing the domain port, Flyway migrations.
└── api              # OpenAPI contract (recipe-api.yaml), generated server
                     # interfaces + DTOs (openapi-generator), controllers
                     # implementing the generated interfaces, MapStruct mappers,
                     # exception handler, DI wiring of application use cases.
```

- **`domain`** — `Recipe` (aggregate, validates its own invariants: name
  present, servings > 0, at least one ingredient), `RecipeFilter` (value object
  holding the five optional criteria + paging), `RecipeRepository` interface
  (the port: save, findById, delete, search(filter)). Reusable in any runtime —
  a CLI, a batch job, another service.
- **`application`** — one use-case class per operation, each a single public
  method against the port. One use-case execution equals one transaction; since
  this module is Spring-free, the `@Transactional` demarcation physically lives
  in the infrastructure adapter, not here.
- **`infrastructure`** — `RecipeEntity`/`RecipeIngredient` JPA mapping distinct
  from the domain model, `RecipeSpecifications` (one static method per filter
  criterion, composed with `Specification.allOf`), and `RecipeRepositoryAdapter`
  translating port calls to Spring Data. Ingredient *exclusion* is a correlated
  `NOT EXISTS` subquery; *inclusion* one `EXISTS` per ingredient (AND
  semantics). Instruction search delegates to the full-text predicate (§5).
- **`api`** — thin HTTP edge, contract-first. `src/main/resources/openapi/recipe-api.yaml`
  is the single source of truth for the API; the openapi-generator Maven plugin
  generates the server interfaces and request/response DTOs into
  `target/generated-sources` at build time (never committed, never edited).
  Controllers implement the generated interfaces and call use cases; MapStruct
  maps generated DTOs ↔ domain in both directions, so no hand-written mapping
  exists anywhere. Bean Validation constraints live in the contract
  (`minimum`, `minLength`, `required`) and are emitted onto the generated DTOs.

Separate JPA entity vs domain model is deliberate: it keeps the domain
framework-free (reusability driver) at the cost of one mapping, which MapStruct
also generates.

## 4. API design

Designed up front as `recipe-api.yaml` (step 1 of the build order) — everything
in this section is a rendering of that contract, not of the code. Base path
`/api/v1`. JSON in/out. Errors are RFC 7807 problem documents.

| Method | Path | Purpose | Success | Notable errors |
|---|---|---|---|---|
| POST | `/recipes` | Create recipe | 201 + Location | 400 validation |
| GET | `/recipes/{id}` | Fetch one | 200 | 404 |
| PUT | `/recipes/{id}` | Full update | 200 | 400, 404 |
| DELETE | `/recipes/{id}` | Remove | 204 | 404 |
| GET | `/recipes` | List + filter | 200 (paged) | 400 bad params |

`PUT` is a deliberate extension beyond the assignment (which asks only for add,
remove, fetch): "manage my favourite recipes" without a way to correct one is
an incomplete CRUD story, and the cost is one endpoint on an already-built
stack.

Filter query parameters on `GET /recipes` (all optional, all combinable):

| Parameter | Type | Semantics |
|---|---|---|
| `vegetarian` | boolean | Exact match |
| `servings` | int ≥ 1 | Exact match |
| `includeIngredients` | repeated string | Recipe must contain **all** (case-insensitive) |
| `excludeIngredients` | repeated string | Recipe must contain **none** (case-insensitive) |
| `instructionsContain` | string | Full-text match on instructions (word-based, stemmed, ranked) |
| `page` | int ≥ 0, default 0 | Zero-based page index |
| `size` | int 1–100, default 20 | Page size, hard-capped at 100 |
| `sort` | string, e.g. `name,asc` | Allowed fields: `name`, `servings`, `createdAt`; default below |

All three paging parameters are declared explicitly in the contract (not
Spring's `Pageable` resolver), so the generated interfaces carry them as plain
typed arguments.

Default ordering when `sort` is absent: full-text rank (best match first) if
`instructionsContain` is present, otherwise `createdAt,desc`.

### Worked examples

The same example data appears in three places without divergence: in this
document, in `recipe-api.yaml` as `examples:` blocks (rendered by Swagger UI),
and verbatim in the E2E fixtures.

**Create — `POST /api/v1/recipes`**

Request:

```http
POST /api/v1/recipes HTTP/1.1
Content-Type: application/json

{
  "name": "Potato gratin",
  "vegetarian": true,
  "servings": 4,
  "ingredients": ["potatoes", "cream", "cheese", "garlic"],
  "instructions": "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. Bake in the oven at 180°C for 45 minutes until golden."
}
```

Response — `201 Created`:

```http
HTTP/1.1 201 Created
Location: /api/v1/recipes/0c9c9a3e-5b1f-4c47-9a2d-7e8f13d24a6b
Content-Type: application/json

{
  "id": "0c9c9a3e-5b1f-4c47-9a2d-7e8f13d24a6b",
  "name": "Potato gratin",
  "vegetarian": true,
  "servings": 4,
  "ingredients": ["potatoes", "cream", "cheese", "garlic"],
  "instructions": "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. Bake in the oven at 180°C for 45 minutes until golden.",
  "createdAt": "2026-07-03T12:00:00Z"
}
```

**Validation failure — `POST /api/v1/recipes` with `servings: 0` and no name**

Response — `400 Bad Request`:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/recipes",
  "errors": [
    { "field": "name", "message": "must not be blank" },
    { "field": "servings", "message": "must be greater than or equal to 1" }
  ]
}
```

**Fetch missing recipe — `GET /api/v1/recipes/{unknown-id}`**

Response — `404 Not Found`:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Recipe 6f1e2d3c-0000-0000-0000-000000000000 not found",
  "instance": "/api/v1/recipes/6f1e2d3c-0000-0000-0000-000000000000"
}
```

**Delete — `DELETE /api/v1/recipes/{id}`** → `204 No Content` (empty body);
subsequent `GET` on the same id returns the 404 document above.

**Combined filter — vegetarian, 4 servings, with potatoes, without salmon,
mentioning "oven"**

Request:

```http
GET /api/v1/recipes?vegetarian=true&servings=4&includeIngredients=potatoes&excludeIngredients=salmon&instructionsContain=oven&page=0&size=20 HTTP/1.1
```

Response — `200 OK` (paged; "Potato gratin" matches, a salmon traybake seeded
alongside it does not):

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": [
    {
      "id": "0c9c9a3e-5b1f-4c47-9a2d-7e8f13d24a6b",
      "name": "Potato gratin",
      "vegetarian": true,
      "servings": 4,
      "ingredients": ["potatoes", "cream", "cheese", "garlic"],
      "instructions": "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. Bake in the oven at 180°C for 45 minutes until golden.",
      "createdAt": "2026-07-03T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Bad filter parameter — `GET /api/v1/recipes?servings=abc`** → `400 Bad
Request` problem document with `detail` naming the parameter and expected type.

The paged envelope (`content`/`page`/`size`/`totalElements`/`totalPages`) is an
explicit `RecipePage` schema in the contract — never Spring's raw `Page`
serialization, which is unstable across versions and would leak framework
internals into the API.

## 5. Data model

Two tables, managed by Flyway migration `V1__create_recipes.sql`:

- **`recipe`** — `id UUID PK`, `name varchar(200) NOT NULL`,
  `vegetarian boolean NOT NULL`, `servings int NOT NULL CHECK (servings > 0)`,
  `instructions text NOT NULL`, `created_at timestamptz NOT NULL`, and
  `instructions_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', instructions)) STORED`.
- **`recipe_ingredient`** — `recipe_id UUID FK (ON DELETE CASCADE)`,
  `name varchar(100) NOT NULL`; ingredients are plain strings owned by the
  recipe, lower-cased on write so include/exclude is plain equality.

Indexes:

- PK on `recipe.id`.
- `recipe_ingredient(recipe_id, name)` composite — serves the EXISTS/NOT EXISTS
  subqueries.
- **GIN index on `recipe.instructions_tsv`** — serves full-text search at any
  volume.
- Partial/btree indexes on `vegetarian` and `servings` are deliberately omitted:
  low-cardinality columns; Postgres combines the GIN and subquery results fine.
  Add only if `EXPLAIN ANALYZE` on realistic data says otherwise.

Full-text predicate in JPA: the Specification uses
`cb.isTrue(cb.function("fts_match", Boolean.class, root.get("instructionsTsv"), cb.literal(term)))`
backed by a small SQL function
`fts_match(tsv tsvector, query text) → tsv @@ websearch_to_tsquery('english', query)`
created in the same migration. This keeps full-text composable with all other
Specifications — no native-query fork of the search path (see challenge 2).

## 6. Cross-cutting concerns

- **Statelessness (scalability)** — no HTTP session, no in-process state, no
  local file storage; any number of API replicas can run against one database.
  Pagination is mandatory on the list endpoint (capped `size`), so no request
  can degrade a node.
- **Validation** — constraints declared once, in the OpenAPI contract
  (`required`, `minimum: 1` servings, `minItems: 1` ingredients, `maxLength`s);
  openapi-generator emits them as Bean Validation annotations on the generated
  DTOs. Domain invariants validated again in the `Recipe` aggregate — the domain
  module cannot be corrupted by a different adapter reusing it.
- **Mapping** — MapStruct interfaces only (`RecipeApiMapper` in api,
  `RecipeEntityMapper` in infrastructure); a build fails on unmapped fields
  (`unmappedTargetPolicy = ERROR`), so mapping can't silently drift.
- **Error handling** — one `@RestControllerAdvice` mapping domain
  `RecipeNotFoundException` → 404, validation → 400, fallback → 500 with no
  internals leaked.
- **Logging** — structured request logging at the controller boundary; SQL
  logging off by default, on in `dev` profile.
- **Configuration** — `application.yaml` with `dev` and `prod` profiles; DB
  credentials only from environment variables in `prod`; no secrets in the repo.
- **API docs** — the hand-written `recipe-api.yaml` (with descriptions and
  example requests/responses, including one combined-filter example) is served
  as-is by Swagger UI at `/swagger-ui.html`. No springdoc code scanning: the
  contract is the documentation, and the generated interfaces guarantee the
  implementation matches it.
- **Auth, rate limiting, caching** — out of scope (assumptions); next steps.

## 7. Deployment, operations, and test strategy

- Multi-stage `Dockerfile` (Maven build stage → JRE 21 runtime, non-root user).
- `docker-compose.yaml`: `api` + `postgres:16` with a named volume and
  healthchecks; API waits on DB health. Scale-out demo: `docker compose up --scale api=3`
  behind the compose-provided round-robin DNS works because the service is
  stateless.
- `GET /actuator/health` (liveness/readiness) as orchestrator probes;
  `/actuator/metrics` exposed for scraping.
- README: prerequisites, one-command start, Swagger URL, example curl requests,
  how to run each test tier, module map with one line per module.

Test strategy — E2E first (TDD), inner tiers added as layers are built; each
tier has a distinct purpose, no tier repeats another:

| Tier | Scope | Purpose |
|---|---|---|
| E2E tests (written first, from the contract) | `api`, `@SpringBootTest` + REST Assured + Testcontainers | Executable rendering of `recipe-api.yaml`: every endpoint, every filter criterion, the combined objective scenario, validation errors, RFC 7807 shape, pagination caps. Written before any implementation exists; the whole build turns them green incrementally |
| Domain unit tests | `domain` module, plain JUnit | Invariants and value-object behaviour, zero infrastructure |
| Use-case unit tests | `application`, Mockito on the port | Orchestration logic, error paths |
| Specification integration tests | `infrastructure`, `@DataJpaTest` + Testcontainers | Filter predicates in isolation: exclude/include interaction, full-text ranking order — failure localization the E2E tier can't give |
| Architecture tests | ArchUnit in a shared test module | domain depends on nothing; application depends only on domain; no `jakarta.persistence`/`org.springframework` imports outside infrastructure/api; controllers never touch repositories |
| Quality gates | JaCoCo ≥ 80% line + branch on domain/application (default build); PIT ≥ 75% mutants killed on domain/application (`-Pmutation` profile) | Fails the build if tests don't actually assert behaviour |

Until a feature is implemented, its E2E tests are tagged `@Tag("red")` and
excluded from the default build (run via `-Pe2e-all`), so the build stays green
while the red suite tracks remaining scope — TDD without a permanently broken
`main`.

PIT is scoped to `domain` + `application` only — mutation testing the JPA
adapter or controllers is slow and low-signal; the core logic is where mutants
must die.

**Fixtures and assertion depth** — two rules apply to every tier:

- *Fixtures are full-blown, realistic recipes*, not minimal stubs. A shared
  `RecipeTestBuilder`, published as a `test-jar` so every module reuses it,
  produces complete recipes with plausible names, real multi-ingredient lists,
  and multi-sentence instructions (the "Potato gratin" example from §4 is one
  of them), with builder overrides per test for the one attribute under test.
  Filter and E2E tests seed a fixture *set* deliberately designed so every
  filter criterion has matching **and** non-matching recipes — a filter test
  that couldn't fail (nothing to wrongly include or exclude) proves nothing.
- *Assertions are detailed, never existence checks.* Every E2E/API test asserts
  the exact status code, the response headers that matter (`Location`,
  `Content-Type: application/problem+json`), and the full field-level payload —
  every field of the returned recipe compared to the fixture, not just the id;
  error tests assert the problem document's `status`, `title`, and the specific
  field detail. Business-rule outcomes are asserted explicitly: created recipes
  are retrievable with identical field values, deleted recipes yield 404
  afterwards, excluded-ingredient results are verified to contain none of the
  excluded ingredient (asserting on returned content, not just result count),
  and full-text results assert rank order. `assertThat(result).isNotNull()`-
  style assertions are treated as review failures; the PIT gate (§ quality
  gates) exists to catch exactly these.

## 8. Caveats

1. **Full-text search is word-based, not substring-based.** "oven" matches, and
   "baking" matches "bake" (stemming) — but "ove" matches nothing, unlike the
   `LIKE` approach. If reviewers expect substring semantics, add a fallback
   `ILIKE` predicate for quoted terms; the Specification structure allows both.
2. **Ingredient matching is exact (case-insensitive), not fuzzy.** Ingredients
   are free-text strings: a recipe listing "potato" will not match
   `includeIngredients=potatoes`. Acceptable while one user writes both the
   recipes and the queries; when it becomes a problem, add normalization
   (singular/plural stemming or a canonical ingredient table — see next steps).
3. **English-only text configuration.** `to_tsvector('english', …)` stems in
   English; Dutch or mixed-language instructions degrade to exact-word matching.
   When multilingual support is needed, store a language column and index per
   language.
4. **Domain/entity model separation costs a second model + mapper.** Worth it
   for the reusability driver; if the project stays a single small service
   forever, this is the first ceremony to collapse (merge entity into domain,
   drop one mapper).
5. **Multi-module Maven adds build ceremony.** Four poms and a parent must stay
   consistent; if iteration speed suffers, the same boundaries survive as
   packages in one module with the ArchUnit rules unchanged — the tests, not the
   poms, are the real enforcement.
6. **No authentication, single-user.** The moment "my recipes" means multiple
   users, add an `owner` column and JWT resource-server security — retrofit
   before real data accumulates.
7. **Hard delete with no audit trail.** Switch to soft delete if recoverability
   is ever required; historical deletes are gone forever.
8. **Horizontal scaling covers the API tier only.** Postgres remains a single
   writer; at sustained write-heavy load, introduce read replicas for the search
   endpoint first, then partitioning. The stateless design makes the API tier a
   non-issue at that point.
9. **The contract is now the hardest thing to change.** Generated interfaces
   mean any `recipe-api.yaml` edit ripples through DTOs, mappers, and E2E tests
   at once — that's the point, but it makes mid-implementation API redesign
   expensive. Spend the design effort up front (build-order step 1); if the
   contract churns anyway, batch changes rather than editing per-endpoint.
10. **Generated sources live in `target/`, not the repo.** IDE must run
    `mvn generate-sources` after clone before the project compiles; documented
    in the README. Never hand-edit generated classes — customization goes
    through generator config (`configOptions`) or MapStruct.

## 9. Challenges

1. **Correct exclude semantics in the dynamic query.** A join-based approach
   returns recipes containing salmon when combined with other criteria, or
   duplicates rows on multi-ingredient joins. Approach: correlated `EXISTS` /
   `NOT EXISTS` subqueries per ingredient — no joins on the collection, no
   duplicates, no `DISTINCT`. Fallback: native query with the same shape if the
   Criteria subquery code becomes opaque.
2. **Composing full-text search with JPA Specifications.** Hibernate has no
   portable `@@`/`tsquery` support, and forking the search path into a native
   query would duplicate all other filters (repetition driver violated).
   Approach: the `fts_match` SQL function + `cb.function(...)` predicate keeps
   one query path. Fallback: Hibernate 6 `FunctionContributor` registering the
   `@@` operator directly; last resort is a native query that re-implements only
   the combined-filter search.
3. **Keeping four modules from becoming ceremony.** The "show off, don't
   over-complicate" line is exactly here. Approach: modules contain no
   speculative abstractions — one port, one adapter, use cases as plain classes;
   ArchUnit rules document *why* each boundary exists. If a module would contain
   two classes and no rule, it doesn't get created.
4. **Mutation testing inside the time budget.** PIT can dominate build time and
   drown the signal in surviving-but-irrelevant mutants. Approach: scope PIT to
   domain + application, run it in a separate Maven profile (`-Pmutation`) not
   the default build, and set a realistic threshold (~75% killed) rather than
   chasing 100%.
5. **Proving the queries against real Postgres.** Generated tsvector columns,
   GIN behaviour, and case-insensitivity don't exist in H2 — in-memory fallback
   is no longer even possible. Approach: Testcontainers everywhere; document
   Docker as a hard test-time prerequisite in the README.
6. **Making openapi-generator output fit the architecture.** Default `spring`
   generator output (delegate pattern off, wrong packages, springdoc
   annotations, Java 8 types) fights the module layout and clean-code driver.
   Approach: pin the generator version and configure once —
   `interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`, target package
   under the api module, `openApiNullable=false` — and treat generator warnings
   as errors. Fallback: if a specific shape is ungeneratable (e.g. RFC 7807
   responses), model it in the contract as a shared `Problem` schema rather
   than fighting the generator.
7. **TDD with a red E2E suite against generated stubs.** All E2E tests are
   written when zero behaviour exists, so the suite must fail for the right
   reason (501/assertion, not compile errors or 404-from-missing-route).
   Approach: generated interfaces default every operation to 501 Not
   Implemented, so the red suite compiles and runs from day one; the
   `@Tag("red")` exclusion keeps the default build green while scope burns
   down (§7).

## 10. Build order (~13 h estimated, cut path below)

Contract-first, then test-first: the API contract and its executable E2E
rendering exist before any implementation; every later step turns part of the
red suite green. Each milestone leaves a working, demonstrable system:

1. **OpenAPI contract + code generation (~1.5 h)** — write `recipe-api.yaml` in
   full (paths, schemas, constraints, RFC 7807 `Problem` schema, examples);
   wire the openapi-generator Maven plugin (`interfaceOnly`, Spring Boot 3,
   Bean Validation); parent pom + four modules; stub controllers implementing
   the generated interfaces returning 501; Swagger UI serving the contract.
   *The API is fully designed and browsable before any logic exists.*
2. **E2E test suite, red (~2 h)** — REST Assured + `@SpringBootTest` +
   Testcontainers suite written entirely from the contract: every endpoint,
   every filter criterion, the combined objective scenario, validation errors,
   problem-document shape, pagination caps. `RecipeTestBuilder` and the
   realistic fixture set are built here (§7 rules: exact statuses, headers,
   full payloads, business-rule outcomes). All red (501s), tagged out of the
   default build, burned down from here on.
3. **Walking skeleton (~1.5 h)** — ArchUnit rules failing-then-passing, Flyway
   V1 (including tsvector column + GIN), docker-compose; `POST` +
   `GET /recipes/{id}` implemented through all layers. *First E2E tests green.*
4. **Complete CRUD (~1.5 h)** — PUT, DELETE, unfiltered paged list, domain
   invariants, RFC 7807 handler, MapStruct mappers with
   `unmappedTargetPolicy = ERROR`; domain/use-case unit tests. *CRUD E2E green.*
5. **Filter engine (~2 h)** — `RecipeSpecifications` for the four structural
   criteria + pagination; specification integration tests for exclude/include
   interaction. *Filter E2E green — core value delivered here.*
6. **Full-text search (~1.5 h)** — `fts_match` function, Specification
   predicate, rank ordering. *Remaining E2E green; red tag retired.*
7. **Test quality gates (~1.5 h)** — JaCoCo gate wired into the default build,
   PIT profile on domain/application, kill surviving mutants that reveal weak
   assertions.
8. **Polish and docs (~1.5 h)** — Dockerfile, actuator probes, profiles,
   request logging, `--scale api=3` smoke check, README (module map, test-tier
   guide, `generate-sources` note), final review pass.

Cut path if time runs out (~13 h → ~10 h), in order:

1. Step 7 (quality gates) — coverage and mutation tooling are additive; the
   tests themselves remain. Listed in next steps instead.
2. Step 6 downgraded, never dropped — instruction search is an acceptance
   criterion. Fallback: replace the full-text predicate with a case-insensitive
   `LIKE` inside the same Specification (~15 min), keep the tsvector column and
   GIN index in the schema, and record the swap as a known caveat. The E2E
   rank-order test stays red-tagged as the honest TODO.
3. Nothing else is cuttable: steps 1–5 are the assignment itself.

## 11. Next steps (further improvements)

Required by the acceptance criteria; also belongs in the delivered README:

- Authentication and per-user recipe ownership (Spring Security, JWT).
- CI pipeline (build, all test tiers, JaCoCo report, image publish).
- Structured ingredients with quantities/units and a canonical ingredient table.
- Multilingual full-text search (language column, per-language index).
- Response caching (Caffeine → Redis when multi-replica) on the search endpoint.
- Read replicas for the search path if write load grows.
- PATCH endpoint for partial updates; optimistic locking via `@Version`.
- Rate limiting at the edge; OpenTelemetry tracing.

## 12. Requirement register

Every requirement from the assignment, as a numbered rule with the concrete
specification details it binds to. These IDs are the vocabulary for the whole
codebase: code, tests, and commits reference them instead of restating intent.

### Functional requirements

| ID | Assignment requirement | Specification rules |
|---|---|---|
| REQ-1 | The application exposes a REST API for managing recipes | JSON REST API under `/api/v1` (§4); contract-first `recipe-api.yaml` is the source of truth (§3); errors are RFC 7807 problem documents |
| REQ-2 | Users can add recipes | `POST /recipes` → 201 + `Location` header + full created payload; 400 problem document on invalid input (§4 create example) |
| REQ-3 | Users can remove recipes | `DELETE /recipes/{id}` → 204 empty body; 404 on unknown id; subsequent `GET` on the deleted id returns 404; hard delete, cascade removes ingredients (§4, §5) |
| REQ-4 | Users can fetch recipes | `GET /recipes/{id}` → 200 single recipe, 404 problem document on unknown id; `GET /recipes` → 200 paged `RecipePage` envelope, default `size=20`, capped at 100 (§4) |
| REQ-5 | Filter by vegetarian or not | `vegetarian` boolean query parameter, exact match (§4) |
| REQ-6 | Filter by number of servings | `servings` integer (≥ 1) query parameter, exact match (§4) |
| REQ-7 | Filter by ingredients to include | `includeIngredients` repeated parameter; recipe must contain **all** listed ingredients; case-insensitive exact name match; one `EXISTS` subquery per ingredient (§4, §5, caveat 2) |
| REQ-8 | Filter by ingredients to exclude | `excludeIngredients` repeated parameter; recipe must contain **none**; case-insensitive; correlated `NOT EXISTS` subquery — result content verified in tests, not just counts (§4, §5, §7, challenge 1) |
| REQ-9 | Filter by searching text within instructions | `instructionsContain` parameter; word-based, stemmed, ranked Postgres full-text search on the `instructions_tsv` GIN-indexed column; rank order is the default sort when present (§4, §5, caveat 1) |
| REQ-10 | Filters are combinable ("without salmon but mentioning oven…") | All five filter parameters compose into one query via JPA Specifications — single query path, no special cases; the assignment's combined scenario is a worked example (§4) and a named E2E test (§7) |

### Non-functional requirements

| ID | Assignment requirement | Specification rules |
|---|---|---|
| REQ-11 | The API is documented | Hand-written OpenAPI contract with descriptions and examples served by Swagger UI; generated interfaces make drift impossible (§2, §4, §6) |
| REQ-12 | All data persisted in a database | PostgreSQL 16, Flyway-versioned schema (§5); no in-memory storage anywhere, including tests (Testcontainers, challenge 5) |
| REQ-13 | The code is production ready | Enforced module boundaries (ArchUnit), stateless horizontally-scalable service, validation at the contract + domain invariants, RFC 7807 errors, health probes, profiles, no secrets in repo, one-command Docker start (§3, §6, §7) |
| REQ-14 | Covered by tests | Six test tiers each with a distinct purpose (§7); realistic fixtures with matching *and* non-matching seeds; field-level assertions on statuses, headers, payloads, and business rules; JaCoCo ≥ 80% and PIT ≥ 75% gates on domain/application |
| REQ-15 | Delivered without reference to the assignment's origin | Delivery constraint in §1: no such references in code, docs, or commit history; assignment text file gitignored |
| REQ-16 | Next steps for further improvements are provided | §11, duplicated in the delivered README |

### Code reference convention

- Reference format is `[REQ-n]`, written exactly like that so it is greppable.
- **Where to reference:** Javadoc of the class or method that *implements* the
  requirement (`RecipeSpecifications.hasNoneOfIngredients` → `[REQ-8]`), E2E
  and integration test display names
  (`@DisplayName("[REQ-10] combined filter: vegetarian, 4 servings, ...")`),
  the OpenAPI operation `description` fields, and commit messages.
- **Where not to reference:** inline comments narrating code ("// check
  servings [REQ-6]" is noise — the Javadoc of the enclosing member carries the
  tag once). One tag at the unit that owns the behaviour, not on every line
  that touches it.
- A requirement may map to several code sites (REQ-13/14 naturally spread);
  every REQ-1…REQ-10 tag must appear in at least one test display name — that
  is the executable traceability check a reviewer can grep for.
