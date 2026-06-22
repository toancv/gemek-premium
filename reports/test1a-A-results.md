# TEST.1a (Option A) — Results: suite GREEN via isolated gemek_test

Date: 2026-06-22 | Branch: deploy/local | Base HEAD: dc03972
Outcome: **142 → 0 failures. 331/331 PASS. BUILD SUCCESS.**

---

## Before → After

| | Total | Pass | Fail | Errors |
|---|---|---|---|---|
| Diagnosis (shared dev `gemek`) | 331 | 189 | **142** | 0 |
| TC attempt (Docker 29 blocked) | 331 | ~17 unit | — | all integration error |
| **Option A (gemek_test, this run)** | **331** | **331** | **0** | **0** |

Remaining buckets: **auth 401 = 0**, **count-pollution = 0 surfaced**, **regression (C) = 0**.

The predicted "second wave" (absolute-count failures from committing classes) did **not** surface in a clean, single-threaded run — the count assertions hold against a freshly-migrated DB. See 1b scope below for why it is still latent.

---

## Mechanism (test-infra only — no production code)

**Dual path in `AbstractIntegrationTest`, selected by `-Dtest.db`:**
- **local (default)** — dedicated `gemek_test` DB on the running Postgres (5433). Created if missing, then **Flyway clean + migrate (V1..V17) once per JVM** in the class static initializer → empty schema → `AdminSeeder` seeds `0900000000 / GemekAdmin2026` on an empty `users` table → login 200. Fully automatic, no manual step.
- **testcontainers (`-Dtest.db=testcontainers`)** — singleton PG+Redis containers (CI path, preserved). Not default locally where Docker Desktop 29.5.2/API 1.54 rejects docker-java 3.4.1.

**Switch:** `System.getProperty("test.db", "local")`. CI runs `mvnw test -Dtest.db=testcontainers`; local `mvnw test` uses gemek_test.

**Credential:** single source of truth = test constants; `application-test.yml app.admin.password = GemekAdmin2026` (carried from prior 1a).

**Redis:** local path reuses the running dev Redis (6380, from `application-test.yml`); TC path containerizes it.

### Safety guard (dev `gemek` can never be cleaned)
- `clean()` runs ONLY in the local path, ONLY against the hardcoded `LOCAL_TEST_URL` ending `/gemek_test`; an explicit `endsWith("/gemek_test")` check throws otherwise.
- The dev `gemek` DB is touched only by a read-only maintenance session that runs `CREATE DATABASE gemek_test` if absent — never cleaned/dropped.
- **Verified post-run:** dev `gemek` = 2081 users (unchanged); `gemek_test` = 98 users (admin + this run's committed rows); `gemek_test` admin phone = `0900000000`.

---

## 1b scope (residual pollution — NOT blocking green)

After the run, `gemek_test` held **98 users** (and other committed rows) left by the **~21 of 33 `@SpringBootTest` classes that are NOT `@Transactional`** and commit via MockMvc. Today this is harmless because:
- The per-JVM `clean()` wipes it before every run (no cross-run carryover).
- Single-threaded sequential ordering keeps each count test's assertions self-consistent.

**It remains latent**: cross-class committed rows accumulate WITHIN a run. Under parallel execution or a different class order, absolute-count assertions (e.g. `UserControllerTest.listUsers_*`, `ApartmentControllerTest $.total`, `BlockControllerTest.listBlocks_pagination`) could break.

**1b (preventive) = make the committing classes self-isolating:** add `@Transactional` rollback where MockMvc writes allow it, or per-test cleanup, to the ~21 non-`@Transactional` classes. Biggest item: the controller/integration suites that create rows without rollback (Resident, Ticket, Amenity, Parking, Vehicle, Contractor, Announcement, Notification).

---

## Files changed (test-infra only)
- `backend/src/test/java/vn/vtit/gemek/support/AbstractIntegrationTest.java` — dual-path (local gemek_test clean+migrate / CI testcontainers) + safety guard.
- `backend/src/test/resources/application-test.yml` — datasource → `gemek_test`.
- (carried from prior 1a) 33 `@SpringBootTest` classes `extends AbstractIntegrationTest`; `app.admin.password = GemekAdmin2026`.
- Evidence: `reports/test1a-A.raw` (331/331 BUILD SUCCESS).

No production code, no auditing (AUD.2/AUD.3) code touched.
