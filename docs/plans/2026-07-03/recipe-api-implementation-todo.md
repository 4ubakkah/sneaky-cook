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
      `git grep -i assignment` clean outside `docs/plans`, except the two
      ignore-file entries that name the gitignored file itself
      (`.gitignore`, `.dockerignore`) — verified 2026-07-06 after an audit
      found five stray prose references and fixed them.

### Step 9 — Final stage: authentication and ownership (spec §13) ✅ (done)

Do not start before steps 1–8 are green. Own contract-first TDD cycle.

- [x] Contract delta in one batched edit: `/auth/register`, `/auth/login`,
      `bearerAuth` scheme + 401 responses on every `/recipes` operation.
- [x] Regenerate sources; existing E2E tests authenticate through the single
      base-class seam (register user per test, attach bearer token via
      `RestAssured.authentication = oauth2(...)`; `.auth().none()` opts out
      for 401 tests) — wiring only, no test content changes. All 115 core
      tests stayed green through the flip.
- [x] New E2E tests (19): auth happy paths/validation, 401 variants
      (missing/garbage/expired token — expired signed with the real configured
      secret via Nimbus), 409 duplicate username, ownership isolation
      (user A never sees user B's recipes; page totals per-owner; foreign id
      → 404, never 403; hijacked PUT leaves content untouched).
- [x] ~~`V2__add_users_and_ownership.sql`~~ → **`V3`** — the V2 slot was taken
      by `add_fts_functions` in step 6; same content: `app_user`,
      `recipe.owner_id` FK NOT NULL, `recipe(owner_id)` index, wipe pre-auth
      fixture rows.
- [x] Custom `AuthenticationEntryPoint` for RFC 7807 401s (Security's default
      empty 401 breaks the error contract).
- [x] Keep domain/application Spring-Security-free: user id enters use cases
      as a plain first argument (`AuthenticatedUser` resolves it from the JWT
      subject at the edge); hashing behind the `PasswordHasher` port (BCrypt
      adapter in infrastructure, `spring-security-crypto` only — no web stack).
- [x] Owner scoping enforced in the query, not after it: `findByIdAndOwner`
      derived query + an `ownedBy` Specification AND-ed into every search;
      specification-tier tests prove foreign rows never load.
- [x] Lost registration races land on the `app_user.username` unique
      constraint; the adapter translates `DataIntegrityViolationException` →
      `UsernameTakenException` via `saveAndFlush`, so racers also get 409.

### Step 10 — Final sanity pass (spec §10) ✅ (done 2026-07-06)

Whole-system verification from a clean state, not a re-read of earlier
checkboxes. Every item below was executed as a live command, not recalled
from memory.

- [x] `mvn clean test` from the repo root, all four modules together against
      a real Postgres (Testcontainers) — **209 tests, 0 failures, 0 errors**
      (27 domain + 14 application + 34 infrastructure + 134 api/E2E/arch).
      Counted from the surefire XML reports, not the console summary — the
      plain-text summary undercounts JUnit 5 `@Nested` classes (reports
      `Tests run: 0` for a class whose tests are all nested; the XML's
      `tests="N"` attribute is the true count).
- [x] `mvn -Pmutation test` on `domain` + `application` — domain 100%
      (10/10), application 88% → 94% after this pass (14/16 → 15/16; the
      one remaining survivor is the accepted equivalent mutant below).
- [x] Full stack brought up for real: Postgres via Docker, the packaged
      Spring Boot jar run against it. `/actuator/health` UP, a live
      `register` → `login` round-trip, Swagger UI reachable.
- [x] Both Bruno collections run with the actual CLI against that live
      instance: `recipe-api-tests` (assertion suite) 44/44 requests, 48/48
      tests; `recipe-api` (exploration) 11/11 requests.
- [x] Delivery-constraint re-audit (REQ-15), run as a command, not assumed:
      `git grep -i assignment` outside `docs/plans` found **five stray prose
      references** that a purely mental review had missed (README, the
      OpenAPI contract, one E2E comment, two Bruno files) — none leaked the
      client name or task text, but the step-8 checklist's "clean outside
      docs/plans" claim was false as written. Reworded all five to drop the
      word entirely and corrected the step-8 checklist to name its one
      legitimate exception (the two ignore-file entries that reference the
      gitignored filename by necessity). Also swept for the client name
      itself and any other assignment-origin identifier — clean.
- [x] Killed the one actionable surviving mutant found while re-verifying
      PIT (`UpdateRecipe.execute()`, return value mutated to `null`
      survived): `UpdateRecipeTest.replacesFieldsKeepsIdentity()` captured
      the argument passed into the mocked `save()` but never asserted on
      `execute()`'s own return value. One assertion closes it. The other
      surviving mutant found (`AuthenticateUser`'s decoy-hash branch) is left
      as-is — it is an equivalent mutant by design: the timing-attack
      defence only needs the decoy comparison to *run*, never its boolean
      result, so no assertion can kill it without weakening the security
      property it protects.

**Lesson**: every number and claim elsewhere in this document and the spec
(test counts, coverage percentages, "clean outside docs/plans") was accurate
*at the time it was written*, but had already drifted by delivery time —
the README's test count matched an earlier run, the delivery-constraint
checkbox predated later prose edits that reintroduced the word it forbade.
A final pass that re-derives every quoted number and re-runs every audited
command, rather than trusting the checkmarks, is not optional polish — it is
the only step that catches this class of drift.

Step-9 gotchas recorded for posterity:

- **Spring Boot 4 ships Jackson 3** — `tools.jackson.databind.ObjectMapper`,
  not `com.fasterxml`; the entry point's import was the only place that
  noticed.
- **`TestEntityManager` moved** in Boot 4 to
  `org.springframework.boot.jpa.test.autoconfigure` (artifact
  `spring-boot-jpa-test`); plain `jakarta.persistence.EntityManager`
  injection is simpler in a `@DataJpaTest` slice and needs no new dependency.
- **Per-test users double as data isolation**: each E2E test registering a
  fresh user makes owner-scoping the isolation mechanism, so the
  delete-all-through-API cleanup is now belt-and-braces rather than
  load-bearing.

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
