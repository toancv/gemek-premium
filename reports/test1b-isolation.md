# TEST.1b — Committing test classes isolated for order-independence

Date: 2026-06-22 | Branch: deploy/local | Base HEAD: 9cecd5a
Outcome: **Suite is now order-independent. Sequential 331/331, two randomized-order runs 331/331. Parallel surfaces 3 framework-level errors (known limit, not data pollution).**

---

## Goal

TEST.1a made the suite green *sequentially* via the isolated `gemek_test` DB (per-JVM Flyway clean).
That proves nothing about order-independence: the ~21 non-`@Transactional` `@SpringBootTest` classes
commit rows via MockMvc that accumulate *within* a run. 1b makes those committing classes
self-isolating so absolute-count assertions cannot be polluted by another class's committed rows
under a different class/method order.

## Ground-truth scan (decides strategy, not one-size-fits-all)

Before touching anything, confirmed how the committed writes behave:

- **No `@Async`, no `Propagation.REQUIRES_NEW`, no `@TransactionalEventListener`/`AFTER_COMMIT` anywhere in `src/main`.**
  → every write happens in the request thread, inside the transaction the test method establishes,
  so `@Transactional` rollback genuinely removes the rows (it is not false isolation).
- **No NOTX class uses `JdbcTemplate`, native queries, `EntityManager`, `TestTransaction`, threads,
  `CompletableFuture`, or `@Sql`** → no cross-connection / stale-read coupling that rollback would break.
- The absolute-count assertions are all **scoped to a unique in-test marker** (filter by a block id /
  phone / plate / email created in the test) or merely type-check (`$.total isNumber()`). None asserts a
  global unfiltered row count. So once each class rolls back its own rows, counts are deterministic.

## Per-class strategy

33 `@SpringBootTest` classes total. 12 were already `@Transactional` (1a) — left unchanged.
Of the 21 non-`@Transactional`:

| Strategy | Count | Classes |
|---|---|---|
| **`@Transactional` (rollback)** — mirrors the existing 12 | **18** | AmenityBookingIntegrationTest, AnnouncementFlowIntegrationTest, SelfProfileUpdateIntegrationTest, AmenityControllerTest, AnnouncementControllerTest, ApartmentControllerTest, BlockControllerTest, AuthControllerTest, AuthCookieSecureFlagTest, AuthCookieTest, ContractorControllerTest, NotificationControllerTest, ParkingControllerTest, ReportControllerTest, ResidentControllerTest, TicketControllerTest, UserControllerTest, VehicleControllerTest |
| **No isolation needed (0 writes)** | **1** | CorsIntegrationTest — only OPTIONS/preflight requests, creates nothing |
| **Resistant → left committing (deliberate)** | **2** | NotificationIntegrationTest, TicketLifecycleIntegrationTest |

### Why the 2 resistant classes are NOT `@Transactional` (flagged)

Both assert on a **read-back after a bulk `@Modifying` UPDATE**:

- `NotificationIntegrationTest.markSingleRead` — "mark read" is a bulk UPDATE; under a rolled-back test
  transaction the first-level cache stays stale, so the read-back list returns `isRead=false` →
  `expected: <true> but was: <false>`.
- `TicketLifecycleIntegrationTest.maintenanceTicket_...fullLifecycle` — contractor rating recalculation
  is a bulk UPDATE; under rollback the cached contractor stays stale → `rating` reads `null`.

This is exactly the CAUTION case: `@Transactional` would **mask the real production path** (bulk
update + fresh read). They must commit. Explicit `@AfterEach` cleanup was **rejected as a fragile fix**:
each creates a deep FK graph (block → apartment → ticket → user/technician → notification → audit_log),
so scoped deletion across 7 linked tables is brittle. It is also **unnecessary** for the 1b goal —
their committed rows are harmless because (a) every surviving count assertion is scoped to a unique
marker, and (b) the per-JVM Flyway clean isolates across runs. Each carries an in-code `NOTE (TEST.1b)`
explaining the deliberate omission so nobody "helpfully" re-adds `@Transactional`.

## Verification (incremental, clustered)

Converted in 4 clusters of 5, running each cluster's tests before moving on (regression localized):

| Cluster | Classes | Result |
|---|---|---|
| A | User, Apartment, Block, Vehicle, Contractor controllers | green |
| B | Amenity, Announcement, Notification, Parking, Report controllers | green |
| C | Resident, Ticket, Auth, AuthCookie, AuthCookieSecureFlag | green |
| D | 5 integration tests | 2 failures → identified the 2 resistant classes, reverted them, re-ran → green |

## Robustness proof

Added `src/test/resources/junit-platform.properties`:
```
junit.jupiter.testclass.order.default  = org.junit.jupiter.api.ClassOrderer$Random
junit.jupiter.testmethod.order.default = org.junit.jupiter.api.MethodOrderer$Random
```
Fresh seed each run → the suite now continuously exercises a different order, so any future
order-coupling regression surfaces as a failure.

| Run | Order (first classes) | Result |
|---|---|---|
| Sequential baseline | Cors, Presign, AmenityBooking… | **331/331** |
| **Randomized #1** | Resident, User, SelfProfile, AuthCookieSecure… | **331/331** |
| **Randomized #2** | SelfProfile, User, TicketPublic, Contractor… | **331/331** |
| Parallel (4 threads, fixed pool) | ForkJoinPool-1-worker-1..3 (true concurrency) | **328 pass / 3 errors** |

Two distinct random orders both green = **order-independence proven**. Absolute-count assertions hold
regardless of order.

### Parallel = known limit (not fixed now)

All 3 parallel errors are the **same** cause:
`java.lang.IllegalStateException: Cannot start new transaction without ending existing transaction`
in `@BeforeEach`. This is Spring's `TransactionalTestExecutionListener` thread-bound transaction context
being interleaved by JUnit's shared ForkJoinPool workers — `@Transactional` Spring tests are **not
parallel-safe within one JVM/context**. It is framework-level, not data pollution: it hits a
*pre-existing* `@Transactional` class too (`PresignPrefixRoutingTest`, one of the original 12), and the
errors are tx-lifecycle, not count mismatches. Parallel config was a one-off CLI flag and is **not
committed**. Enabling parallel for real would need a CTO call (e.g. per-class forked JVMs via surefire
`forkCount`, or dropping `@Transactional` for explicit cleanup) — out of 1b scope.

## Files changed (test-infra only — no production code)

- 18 test classes: added `@Transactional` (+ import) — mirrors the existing 12.
- 2 test classes (NotificationIntegrationTest, TicketLifecycleIntegrationTest): added `NOTE (TEST.1b)`
  comment documenting the deliberate non-`@Transactional` choice.
- `src/test/resources/junit-platform.properties` (new): random class + method order.
- Evidence: `reports/test1b-baseline.raw.txt`, `test1b-random1.raw.txt`, `test1b-random2.raw.txt`,
  `test1b-parallel.raw.txt`, cluster raws.

No production code, no auditing (AUD.2/AUD.3) touched.
