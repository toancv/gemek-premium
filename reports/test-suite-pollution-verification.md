# Test-suite / dev-DB-pollution — verification (2026-06-23)

Read-only. Determines whether the test-isolation / dev-DB-pollution issue is genuinely RESOLVED or merely
avoided. Decisive evidence = repeated + reordered suite runs (a single green run proves nothing). NO code changed.

## VERDICT — **RESOLVED.** The standing open issue can be closed.
- Tests run against an ISOLATED dedicated `gemek_test` DB with a per-JVM Flyway clean+migrate → no contact with
  dev `gemek`, no cross-run pollution.
- Two back-to-back full runs (no manual DB reset between them), each in a DIFFERENT random order, both
  **379/379 green** → repeat-stable AND order-independent.
- The only non-green result is the PARALLEL attempt: **0 failures, 10 errors, ALL the identical framework
  signature** `IllegalStateException: Cannot start new transaction without ending existing transaction` — Spring
  `@Transactional` test-tx is not parallel-safe in one JVM/context. **Not data pollution.** This is the same
  known limitation recorded in TEST.1b (then 3 errors; now 10 as more `@Transactional` test classes were added).

## Prior claim (to verify, not trust)
PROGRESS.md "TEST.1b" (2026-06-22): suite made isolated via `gemek_test` (per-JVM Flyway clean) and
order-independent; sequential 331/331, two randomized runs 331/331; parallel 328/3-errors (framework, not
pollution). The "142 failures" episode (AUD.1 era) was the admin-hash login→401 corruption, since fixed by
`AdminSeeder` plaintext seeding. **This turn re-verifies all of that at the current tree (suite has since grown
331 → 379).**

## Current test-DB setup (file:line evidence)
1. **Isolated test DB.** `backend/src/test/resources/application-test.yml` →
   `spring.datasource.url: jdbc:postgresql://localhost:5433/gemek_test` (dedicated, "NEVER the dev 'gemek' DB").
   `AbstractIntegrationTest` (`backend/src/test/java/vn/vtit/gemek/support/AbstractIntegrationTest.java`) is the
   base of every `@SpringBootTest`: local path (default) targets `gemek_test` and runs Flyway **clean + migrate
   ONCE PER JVM** (`:108 flyway.clean()`), so every `mvn test` starts from an empty schema. Testcontainers path
   exists for CI (`-Dtest.db=testcontainers`) but is not the default locally.
2. **Flyway clean guard (correct).** `AbstractIntegrationTest:97-99` — `if
   (!LOCAL_TEST_URL.endsWith("/" + LOCAL_TEST_DB)) throw new IllegalStateException("Refusing Flyway clean: target
   is not gemek_test ...")`. `LOCAL_TEST_DB = "gemek_test"` (`:49`). Clean can only ever hit `gemek_test`; dev
   `gemek` can never be cleaned.
3. **AdminSeeder skip-if-exists — old failure mode NOT possible now.** `AdminSeeder.java:75`:
   `if (userRepository.existsByRole(UserRole.ADMIN)) { log "skipping"; return; }`. Because the per-JVM Flyway
   `clean()` wipes the schema (admin included) BEFORE the Spring context starts, `existsByRole(ADMIN)` is false
   at seed time → AdminSeeder always re-seeds the test admin (`0900000000` / `GemekAdmin2026`,
   `application-test.yml`) on the empty DB. The old "seeder skips on a pre-polluted shared DB → admin
   missing/corrupt → 142 login-401 failures" mode cannot recur under this setup (it depended on a shared,
   never-cleaned DB + the env-var-`$`-truncated Flyway hash, both gone).

## Multi-run results (the decisive evidence)
Raw logs (UTF-16, re-readable): `reports/p-run1.raw.txt`, `p-run2.raw.txt`, `p-run3.raw.txt`. Each `mvn test`
invocation does its own per-JVM Flyway clean; random class+method order per run via
`backend/src/test/resources/junit-platform.properties` (`ClassOrderer$Random` + `MethodOrderer$Random`).

| Run | Mode | Order | Result | Build |
|-----|------|-------|--------|-------|
| 1 | full suite, sequential | random seed A | **Tests run 379, Failures 0, Errors 0, Skipped 0** | SUCCESS (exit 0) |
| 2 | full suite, sequential, **no DB reset between run1 and run2** | random seed B (distinct) | **Tests run 379, Failures 0, Errors 0, Skipped 0** | SUCCESS (exit 0) |
| 3 | full suite, **parallel** (`-Djunit.jupiter.execution.parallel.enabled=true -D…mode.default=concurrent`) | random | **Tests run 379, Failures 0, Errors 10, Skipped 0** | FAILURE (exit 1) |

### Interpretation
- **Repeat-stable (run1 → run2, no manual reset):** identical 379/379 green. If run1's committed rows (the 2
  by-design committing classes — `NotificationIntegrationTest`, `TicketLifecycleIntegrationTest`, which read back
  after a bulk `@Modifying` UPDATE) had polluted run2, run2 would have failed or changed count. It did not — the
  per-JVM Flyway clean isolates runs. **No live pollution.**
- **Order-independent:** run1 and run2 executed in two DIFFERENT random orders, both green. (Continuous
  order-coupling guard via the randomizer is working.)
- **Parallel:** 379 run, **0 failures, 10 errors, every one** the identical signature
  `IllegalStateException: Cannot start new transaction without ending existing transaction` (verified:
  `grep` count = 10/10, zero non-tx error signatures). This is the Spring `@Transactional` test-transaction
  manager being shared across threads in a single JVM/context — a **framework limitation, NOT data pollution**
  (a pollution failure would be a data/assertion `Failure`, not a tx-manager `Error`, and would not be 100% the
  same signature). Count rose 3 → 10 vs TEST.1b only because more `@Transactional` test classes now exist.

## Remaining root cause / fix direction (parallel only — optional, not a pollution bug)
The pollution issue itself has **no remaining root cause** — it is resolved. The parallel-execution limitation
persists and is purely framework-level: Spring's `@Transactional` test rollback uses a thread-bound transaction
that cannot be shared by concurrent tests in one context. **Minimal fix direction IF the CTO ever wants parallel
(described, NOT applied):** either (a) run parallel with **forked JVMs** (Surefire `forkCount>1` /
`reuseForks=false`) so each fork has its own context + `gemek_test`-style DB (needs per-fork DB names to avoid
the single shared `gemek_test`), or (b) drop `@Transactional` rollback on the parallelized classes and use
explicit per-test cleanup. Both are larger efforts and out of scope here; sequential runs are already a reliable
safety net.

## Bottom line
- Dev-DB-pollution: **RESOLVED** — isolated `gemek_test`, per-JVM clean, guard correct, AdminSeeder safe;
  repeated + reordered runs stable at **379/379**.
- Parallel-safety limitation: **still holds**, confirmed **framework `@Transactional` nesting, not pollution**;
  enabling it is a separate CTO decision (forked JVMs / explicit cleanup).
- Recommendation: the standing test-suite "open issue" can be **closed**; keep sequential execution; track
  parallel as a known, documented, non-blocking limitation.

No code, migration, or test changes made.
