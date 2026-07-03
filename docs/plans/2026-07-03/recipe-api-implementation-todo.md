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
- [x] ~~Float-to-int coercion disabled via Jackson~~ — replaced with
      OpenAPI contract validation (`OpenApiRequestValidationConfig` +
      `InvalidRequestException` handler): wire types are checked against the
      hand-written spec before deserialization, so `servings: 4.5` is rejected
      as a contract violation, not a Jackson tuning knob.
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
      list (NONE) — matching spec §5. `instructionsContain` deliberately
      ignored until step 6 (its tests stay red).

### Step 6 — Full-text search

- [ ] Rank-order assertion lives in the specification tier (deterministic
      fixtures), not E2E — E2E only asserts match sets.
- [ ] Generated `tsvector` column freshness after UPDATE is E2E-tested
      (updated instructions visible to search) — no caching layer may sit in
      front of it.
- [ ] Multi-word queries use AND semantics via `websearch_to_tsquery`
      (E2E-tested with "bake oven").

### Step 8 — Polish and delivery

- [ ] README: document Docker as a hard test-time prerequisite and the
      `mvn generate-sources` step needed after clone (generated sources live in
      `target/`, never committed).
- [ ] Delivery check (REQ-15): `assignment-description.txt` stays gitignored;
      verify no origin references in code, docs, or commit history before
      delivery (`git log --all --full-history` + grep).
- [ ] Swagger UI is served from the `swagger-ui` webjar with a static page —
      a deliberate deviation from the spec's original springdoc choice
      (springdoc disables its UI together with code scanning). Keep the
      contract file the single source of truth.

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
| `servings: 4.5` rejected, never truncated | OpenAPI contract validation + E2E | Raw-JSON review: checked against spec `type: integer`, not Jackson knobs |
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
- **Framework test engines can silently not run**: ArchUnit's JUnit engine
  reported `Tests run: 0` under surefire without failing the build. After any
  test-infrastructure change, verify the *count* of executed tests, not just
  BUILD SUCCESS.
