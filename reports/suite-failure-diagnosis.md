# Backend Suite Failure Diagnosis — READ-ONLY

Date: 2026-06-22 | Branch: deploy/local | HEAD: 200c6ea
Run: `backend/mvnw.cmd test` → captured in `reports/suite-run.raw.txt`

---

## Headline

**The AUD.1 "all 142 = corrupt admin BCrypt hash (bucket A)" classification is WRONG / STALE.**

- The admin hash in the shared dev DB today is a **valid** `$2a$12$…` 60-char BCrypt. Not corrupt.
- Live login against the running stack with the correct credentials returns **HTTP 200** (proof password + hash are fine).
- All 142 failures are an **auth-401 cascade rooted in shared-dev-DB state (bucket B)**, not a corrupt hash.
- The corrupt-hash root cause AUD.1 cited was **already fixed** in `V2__seed_admin.sql` (Flyway placeholder seeding removed; admin now hashed in-JVM by `AdminSeeder`).

| Bucket | Symptom def | Count (by symptom) | Count (by ROOT CAUSE) |
|--------|-------------|--------------------|------------------------|
| A — corrupt admin hash → 401 | auth returns 401 | 142 (all are 401) | **0** (disproven — hash valid, login works) |
| B — shared dev-DB state/pollution | committed rows / state mismatch | 0 surfaced | **142** (true cause; see below) |
| C — genuine regression / other | any non-pollution bug | **0** | **0** |

Total failing: **142 / 331** (Failures: 142, Errors: 0, Skipped: 0). Errors=0 confirms the old `JWT_SECRET` WeakKeyException (TEST-REGRESSION.md, 06-04) is also resolved — `application-test.yml` now sets a 512-bit `jwt.secret`.

---

## Evidence — every failure is the same

`grep "expected:" reports/suite-run.raw.txt` → **142 × `Status expected:<200> but was:<401>`**, zero of any other message.

The per-method tally shows almost all originate in the `@BeforeEach`:

```
19  ResidentControllerTest.obtainAdminToken:64   ...was:<401>
15  AmenityControllerTest.setUp:92->login:300    ...was:<401>
13  TicketLifecycleIntegrationTest.setUp->login  ...was:<401>
13  TicketControllerTest.setUp:82->login:106     ...was:<401>
 9  VehicleControllerTest.obtainAdminToken:63    ...was:<401>
 9  BlockControllerTest.obtainAdminToken:59      ...was:<401>
 …  (every @SpringBootTest class: admin login in setup → 401 → null token → all methods 401)
 4  AuthControllerTest (login_validCredentials itself → 401)
```

Whole classes fail 100% (Amenity 15/15, Block 9/9, Ticket 13/13, Resident 19/19) because their setup logs in as admin to obtain a token; that login 401s, so every authenticated request in the class 401s.

---

## Root cause (single, decisive)

### 1. Tests run against the SHARED Docker dev DB — no isolation
`backend/src/test/resources/application-test.yml`:
```yaml
spring.datasource.url: jdbc:postgresql://localhost:5433/gemek   # the live dev Postgres
```
- **No** `PostgreSQLContainer`, **no** `@DynamicPropertySource`, **no** `@Testcontainers`, **no** abstract base, **no** `@Sql` anywhere in `src/test/java`. (`TcpDockerClientProviderStrategy` + the "using Testcontainers" Javadoc in test classes are **aspirational/stale** — the container is never started.)
- So `@ActiveProfiles("test")` points all 33 `@SpringBootTest` classes at the running dev Postgres (5433 = host proxy to `gemek-postgres`).

### 2. The dev DB is pre-seeded (polluted) → `AdminSeeder` skips → test-expected admin never exists
- `gemek-postgres` currently holds **2081 users** incl. demo admins `admin01@demo.local`, `admin02@demo.local` (loaded by bulk SQL — see `reports/seed-demo.log`).
- `AdminSeeder` is **idempotent**: `if (userRepository.existsByRole(ADMIN)) return;`. Admins already exist → it **skips** entirely → it never creates the admin the tests expect.
- The tests hardcode login **phone `0900000000`** / password `GemekAdmin2026`. The dev DB's `admin@gemek.vn` has phone **`0900000001`**; **zero** users have phone `0900000000`.
  - `POST /api/auth/login {0900000000, GemekAdmin2026}` → **401** (no such user).
  - `POST /api/auth/login {0900000001, GemekAdmin2026}` → **200** (correct creds; password & hash are valid).

### 3. Secondary config bug — test-profile admin password mismatch
- `application-test.yml` sets `app.admin.password: Admin@123456`, but every test constant is `GemekAdmin2026`.
- On a *clean* isolated DB, `AdminSeeder` would seed phone `0900000000` (its default — matches tests) but password `Admin@123456` → tests sending `GemekAdmin2026` would **still 401**.
- So fixing isolation alone is insufficient; the test admin password must also be aligned. (1-line.)

### Why "corrupt hash" is disproven
`V2__seed_admin.sql` is now a no-op placeholder ("seeding now handled by AdminSeeder … to avoid Docker Compose $-interpolation corrupting BCrypt hashes"). `AdminSeeder` hashes plaintext in-JVM with the BCrypt-12 encoder → no env `$`-interpolation path exists anymore. DB row confirms a valid 60-char `$2a$12$` hash. AUD.1's diagnosis matches the **06-04** dev-DB state, not today's.

---

## Latent bucket B hidden behind the cascade

Because every test dies at the `@BeforeEach` login (401), the **absolute-count / listing assertions never execute**. They are still at risk once auth is restored against a non-clean DB:
- `UserControllerTest.listUsers_*` — asserts against a table that currently has **2081** rows.
- `ApartmentControllerTest …$.total value(2)`, `BlockControllerTest.listBlocks_pagination` — global counts.
- ~21 of 33 `@SpringBootTest` classes have **no** `@Transactional` (only 12 do), so via MockMvc (all 22 use MockMvc; 0 use real HTTP) they **commit** rows that are not rolled back → cross-class pollution within a single run.

This means: a naive auth-only fix that keeps the shared DB may convert some 401s into **count-mismatch** failures (a second wave). A proper isolated, freshly-migrated DB makes both the 401 cascade AND the count assertions deterministic.

---

## Fix size: SMALL-to-MODERATE (bounded — NOT a 142-test rewrite)

All 142 share **one** cascade root. This is not a per-test rearchitecture. Estimated touch: 1 new abstract base test + `@DynamicPropertySource` wiring + 1 line in `application-test.yml`. The risk that pushes it toward "moderate" (not "large") is the latent cross-class count pollution, which a per-run/per-class clean DB resolves.

### Recommended fix sequence (smallest-first; most green per change)

1. **Align test admin password (TRIVIAL, 1 line).** Set `application-test.yml` `app.admin.password: GemekAdmin2026` (or change test constants — pick one source of truth). Prerequisite for any green.
2. **Give tests an isolated, freshly-migrated DB (MODERATE, ~1 file).** Wire the Testcontainers the code already claims: a shared abstract base with a singleton `PostgreSQLContainer` + `@DynamicPropertySource` overriding `spring.datasource.*` (+ Redis). Fresh empty DB → Flyway runs → `AdminSeeder` seeds admin `0900000000`/`GemekAdmin2026` → auth works → counts deterministic. Expected to restore ~all 142.
   - Cheap interim (NOT durable): reset the dev DB to a clean migrated state + step 1. Restores green for one run but re-pollutes on the next (committing classes), so only a stopgap.
3. **Eliminate residual cross-class pollution if it surfaces (CONDITIONAL).** Within a shared container the ~21 non-`@Transactional` classes still commit; if absolute-count tests then fail, add `@Transactional` to committing classes or per-class DB reset. Do this only if step 2 leaves count failures.

Smallest change that restores the most green: **step 1 + step 2 together.**

---

## Raw evidence
| File | Contents |
|------|----------|
| `reports/suite-run.raw.txt` | Full `mvnw test` output — 331 run / 142 fail / 0 err |
| live `psql` | admin@gemek.vn phone=0900000001; 0 users at 0900000000; 2081 users total; hash `$2a$12$` len 60 |
| live `curl` | login 0900000000→401; login 0900000001→200 |
| `application-test.yml` | datasource → localhost:5433/gemek; admin password Admin@123456 |
| `V2__seed_admin.sql` | placeholder no-op; seeding moved to AdminSeeder (corrupt-hash path removed) |
