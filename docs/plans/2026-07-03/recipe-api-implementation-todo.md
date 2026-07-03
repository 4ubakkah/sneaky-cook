# Recipe API — Implementation TODO and Lessons Learned

Living document, companion to the
[design solution specification](recipe-api-design-specification.md).
Revise while implementing each build-order step: tick items off, add new
findings, never delete — strike through with the resolution noted.

## Carried-forward obligations per build step

### Step 3 — Walking skeleton

- [ ] Verify openapi-generator actually emits `@Pattern` for the
      non-whitespace patterns added to `name`, `instructions`, and ingredient
      items — the whitespace-only E2E tests depend on it; if the generator
      drops the constraint, enforce it in the domain invariants instead.
- [ ] `Location` header must be present on 201 (E2E asserts it and round-trips
      it with a GET).
- [ ] Ingredient lower-casing on write happens in the domain aggregate (create
      *and* update paths — both are tested).

### Step 4 — Complete CRUD

- [ ] RFC 7807 handler must also cover Jackson parse/type errors
      (`HttpMessageNotReadableException`) — raw-JSON E2E tests assert
      `application/problem+json` for malformed JSON and type mismatches, which
      Spring's default error body does not satisfy.
- [ ] Disable Jackson float-to-int coercion (`ACCEPT_FLOAT_AS_INT`) so
      `servings: 4.5` is rejected, never truncated (codified decision, E2E-tested).
- [ ] Unknown extra JSON fields are ignored (Boot default — keep it; E2E-tested).
- [ ] `createdAt` is server-managed and immutable across PUT (E2E-tested).
- [ ] An `id` smuggled into the PUT body must be ignored (overposting; E2E-tested).
- [ ] Validation-error problem documents carry an `errors[]` array with
      `field` + `message` per violation (contract schema `FieldError`).
- [ ] Remove `@Tag("red")` per test class as it turns green; the already-green
      415 wrong-content-type test rides along with `RawJsonRequestE2eTest`.

### Step 5 — Filter engine

- [ ] `sort` parameter: reject disallowed fields and missing direction with
      400 (contract pattern `^(name|servings|createdAt),(asc|desc)$` on a query
      parameter may not be enforced by generated code — verify, else validate
      explicitly).
- [ ] Page envelope numbers must reflect *filtered* totals (E2E-tested).
- [ ] Default ordering `createdAt,desc` when no sort given (E2E-tested against
      seeding order).
- [ ] Specification-tier test: ingredient rows are removed with their recipe
      (FK cascade) — not observable through the API, deliberately not E2E-tested.
- [ ] Consider `RecipeTestBuilder` extraction to a shared `test-jar` once a
      second module needs fixtures; deferred to avoid a wrong-direction
      dependency on the api module (spec §7 wants it shared eventually).

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
| `servings: 4.5` rejected, never truncated | Step-4 Jackson config + E2E | Raw-JSON review: Jackson coerces by default |
| Unknown extra fields ignored (201) | Boot default + E2E | Raw-JSON review |
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
- **The E2E burn-down count is the progress meter**: 89 red as of this
  document; update this number when steps land.
