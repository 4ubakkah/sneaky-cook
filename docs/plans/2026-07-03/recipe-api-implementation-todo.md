# Recipe API — Implementation TODO and Lessons Learned

Living document, companion to the
[design solution specification](recipe-api-design-specification.md).
Revise while implementing each build-order step: tick items off, add new
findings, never delete — strike through with the resolution noted.

## Carried-forward obligations per build step

### Step 3 — Walking skeleton ✅ (done)

- [x] Verify openapi-generator actually emits `@Pattern` for the
      non-whitespace patterns — **confirmed**: emitted on `name`,
      `instructions`, and ingredient items; whitespace-only E2E tests pass
      through Bean Validation alone. Domain invariants also reject blanks as a
      second line of defence.
- [x] `Location` header present on 201 — E2E green, round-trips with GET.
- [x] Ingredient lower-casing lives in the domain aggregate's canonical
      constructor — every construction path (create *and* the future update)
      goes through it. Create path E2E green; update path stays red until
      step 4.
- [x] ~~ArchUnit `@ArchTest` fields + `@AnalyzeClasses`~~ — surefire never
      discovered the ArchUnit JUnit engine's tests (`Tests run: 0`); rewrote as
      plain JUnit tests calling `ArchRule.check()` against a `ClassFileImporter`
      import. Behaviour identical, discovery reliable.
- [x] `createdAt` truncated to microseconds in `Recipe.createNew` — PostgreSQL
      `timestamptz` precision — so the POST response and later GETs render the
      identical timestamp string (the stability E2E depends on it).

### Step 4 — Complete CRUD ✅ (done)

- [x] RFC 7807 handler covers Jackson parse/type errors
      (`HttpMessageNotReadableException`) — raw-JSON E2E green.
- [x] ~~Float-to-int coercion disabled via Jackson~~ — **deferred**: OpenAPI
      request validation was tried (`openapi-request-validator` +
      `InvalidRequestException` handler) but mapping validator messages to
      stable `errors[].field` values required brittle regex parsing; dropped
      rather than ship that. Jackson still rejects wrong scalar types (string
      for integer/boolean, scalar for array); decimal-to-integer coercion
      (`servings: 4.5` → 4) is not guarded.
- [x] Unknown extra JSON fields ignored (Boot default kept; E2E green).
- [x] `createdAt` immutable across PUT — enforced structurally:
      `Recipe.updatedWith` is the only update path and never touches
      id/createdAt.
- [x] `id` smuggled into the PUT body ignored — same structural argument, plus
      the contract request schema has no `id` field.
- [x] Validation-error problem documents carry `errors[]` — three sources
      normalized: body (`MethodArgumentNotValidException`), query params from
      the `@Validated` generated interface (`ConstraintViolationException`),
      and handler-method validation (`HandlerMethodValidationException`).
      **Gotcha**: the generated interface being `@Validated` means query-param
      violations surface as `ConstraintViolationException` (500 by default!),
      not `HandlerMethodValidationException` — both handlers are needed.
- [x] Untagged: `UpdateRecipeE2eTest` (3 filter/search tests method-red),
      `DeleteRecipeE2eTest`, `RawJsonRequestE2eTest`, `EmptyCatalogueE2eTest`,
      `PaginationAndSortingE2eTest` (1 filtered-totals test method-red), and
      the two create tests from step 3. `ListRecipesFilterE2eTest` stays
      class-red until step 5.
- [x] Unit tiers added: domain invariants (13) + filter value object (5) +
      use cases with Mockito (7).

### Step 5 — Filter engine ✅ (done)

- [x] `sort` parameter validation — the generated interface *does* enforce the
      contract pattern (the `@Validated` interface + `ConstraintViolationException`
      handler from step 4 produce the 400 problem documents); no explicit
      validation code needed.
- [x] Page envelope numbers reflect *filtered* totals — E2E and
      specification-tier tested.
- [x] Default ordering `createdAt,desc` when no sort given — E2E green; the
      adapter adds an `id` tie-breaker so pages stay disjoint under timestamp
      ties.
- [x] Specification-tier FK-cascade test — exercised with **raw SQL** deletes
      (`JdbcTemplate`) so the schema guarantee holds even for deletes that
      bypass Hibernate; needed a `TestEntityManager.flush()` before raw SQL
      could see pending inserts.
- [ ] Consider `RecipeTestBuilder` extraction to a shared `test-jar` — still
      deferred; the infrastructure tier got its own small `DomainRecipes`
      fixture set (domain aggregates, not HTTP maps), which is a different
      shape than the API-tier builder, so no duplication yet.
- [x] `RecipeSpecifications`: one `EXISTS` subquery per included ingredient
      (AND), one correlated `NOT EXISTS` with `IN` for the whole exclusion
      list (NONE) — matching spec §5.

### Step 6 — Full-text search ✅ (done)

- [x] `V2__add_fts_functions.sql`: `fts_match` and `fts_rank` wrapping
      `websearch_to_tsquery` / `ts_rank` — keeps the predicate inside JPA
      Specifications via `cb.function(...)`.
- [x] `instructions_tsv` mapped read-only on `RecipeEntity` for Criteria access.
- [x] `RecipeSpecifications.instructionsContain` + `orderByRelevance`; adapter
      uses relevance sort when `instructionsContain` is present and the client
      sent no explicit `sort` (`RecipeSort.RELEVANCE`).
- [x] Rank-order assertion in specification tier (`RecipeFullTextRankIntegrationTest`),
      match sets in E2E.
- [x] Generated `tsvector` column freshness after UPDATE E2E-tested
      (`UpdateRecipeE2eTest.updatedInstructionsAreVisibleToTextSearch`).
- [x] Multi-word queries use AND semantics via `websearch_to_tsquery` — E2E uses
      `queryParam("instructionsContain", "bake oven")` (raw `%20` in the URL
      string double-encodes under Rest Assured).
- [x] All `@Tag("red")` retired — default build: **0 red / 100 green** E2E;
      **136 green** across all tiers (+5 full-text specification tests).

### Step 7 — Test quality gates ✅ (done)

- [x] JaCoCo ≥ 80% line + branch on `domain` and `application`, bound to the
      default `mvn test` build.
- [x] PIT mutation profile (`mvn -Pmutation test`) on `domain` + `application`
      with ≥ 75% threshold — domain 100%, application 88% (Feb 2026 run).
- [x] `ListRecipesTest` added so the list use case is covered like the others.
- [x] PIT 1.22.1 + `junit-platform-launcher` on the test classpath; requires
      JDK ≤ 23 in practice (Java 26 minion crash observed on Homebrew JDK).

### Step 8 — Polish and delivery ✅ (done)

- [x] README: Docker prerequisite, `mvn generate-sources`, module map, test-tier
      guide, curl examples, delivery checklist (REQ-15).
- [x] Multi-stage `Dockerfile` (Maven 3.9 + Temurin 21 build → JRE 21 runtime,
      non-root `app` user, curl healthcheck).
- [x] `docker-compose.yaml`: `api` + `postgres:16`, readiness healthcheck,
      `depends_on` DB health — scale demo documented in README.
- [x] Actuator: `/actuator/health` (+ liveness/readiness probes) and
      `/actuator/metrics`; covered by `ActuatorE2eTest`.
- [x] Profiles: `dev` (SQL logging), `prod` (no secrets in repo, env-only DB).
- [x] Request logging filter at the HTTP edge (`RequestLoggingFilter`).
- [x] Swagger UI unchanged — static webjar page at `/swagger-ui.html` serving
      the hand-written contract (single source of truth).
- [x] Delivery check (REQ-15): `assignment-description.txt` gitignored;
      `git grep -i assignment` clean outside `docs/plans`.

### Step 9 — Final stage: authentication and ownership (spec §13)

Do not start before steps 1–8 are green. Own contract-first TDD cycle.

- [ ] Contract delta in one batched edit: `/auth/register`, `/auth/login`,
      `bearerAuth` scheme + 401 responses on every `/recipes` operation.
- [ ] Regenerate sources; existing E2E tests authenticate through the single
      base-class seam (register user per test, attach bearer token) — wiring
      only, no test content changes.
- [ ] New red-tagged E2E tests: auth happy paths/validation, 401 variants,
      409 duplicate username, ownership isolation (user A never sees user B's
      recipes; page totals per-owner; foreign id → 404, never 403).
- [ ] `V2__add_users_and_ownership.sql`: `app_user`, `recipe.owner_id` FK,
      `recipe(owner_id)` index; wipe pre-auth fixture rows.
- [ ] Custom `AuthenticationEntryPoint` for RFC 7807 401s (Security's default
      empty 401 breaks the error contract).
- [ ] Keep domain/application Spring-Security-free: user id enters use cases
      as a plain argument; hashing behind the `PasswordHasher` port.

## Codified decisions learned during test reviews

| Decision | Where enforced | Origin |
|---|---|---|
| Whitespace-only `name`/`instructions`/ingredient items are invalid | Contract `pattern` + E2E | Mutation assessment: `minLength: 1` accepted `"   "` |
| `servings: 4.5` rejected, never truncated | ~~OpenAPI contract validation + E2E~~ **deferred** — Jackson may truncate to 4; not guarded without brittle validator integration | Raw-JSON review: checked against spec `type: integer`, not Jackson knobs |
| Unknown extra fields ignored (201) | Contract `additionalProperties: true` + E2E | Raw-JSON review |
| Duplicate recipe names allowed | E2E | Coverage review: contract has no uniqueness |
| PUT is a deliberate scope extension beyond add/remove/fetch | Spec §4 note | Review against the assignment |
| One rule per test; parameterize analogous cases | Test suite structure | Test revision round |
| Wire-level robustness tested with raw JSON strings, not typed builders | `RawJsonRequestE2eTest` + spec §7 rule 3 | Builder-vs-JSON discussion |
| Mutations tested for side effects, not just responses (filters see updates, `createdAt` immutable, no residue on rejected create) | Update/Create E2E | Mutation assessment |
| Auth/ownership is a dedicated final stage (step 9), never a mutation of core stages | Spec §13 + build order | Auth scoping discussion |

## Process lessons

- **Rewrites lose content**: the revision-2 spec rewrite silently dropped the
  ingredient exact-match caveat; it was only recovered by re-reviewing against
  the original assignment. When rewriting a section, diff the old caveat/
  decision lists item by item.
- **Trace every acceptance criterion to a numbered requirement** (spec §12);
  every REQ-1…REQ-10 must appear in at least one test display name — grep
  `\[REQ-` to audit.
- **Red suite must fail for the right reason**: 501 assertion failures, zero
  errors — check `Failures: N, Errors: 0` after every suite change.
- **The E2E burn-down count is the progress meter**; update per step:
  - After step 2: 89 red.
  - After step 3: 66 red / 30 green of 96 E2E tests (suite also grew during
    the sort/filter consistency review), plus 4 green architecture tests.
  - After step 4: 21 red / 79 green of 100 E2E tests — every remaining red is
    filter-engine (step 5) or full-text (step 6) scope. Default build: 99
    green across all tiers.
  - After step 5: 6 red / 94 green of 100 E2E tests — all six are
    `instructionsContain` (full-text, step 6). Default build: 106 green
    (specification tier added 7).
  - After step 6: **0 red / 100 green** of 100 E2E tests. Default build:
    **136 green** across all tiers.
  - After steps 7–8: default build **114 E2E** (+4 actuator) + JaCoCo gate;
    `mvn -Pmutation test` on domain/application (PIT ≥ 75%).
- **Framework test engines can silently not run**: ArchUnit's JUnit engine
  reported `Tests run: 0` under surefire without failing the build. After any
  test-infrastructure change, verify the *count* of executed tests, not just
  BUILD SUCCESS.
