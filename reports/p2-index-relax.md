# P2 — Relax `uq_residents_active_user` to `(user_id, apartment_id)` (MIGRATION GATE)

**Date:** 2026-06-23 · **Branch:** `deploy/local` · **Type:** MIGRATION gate (index-only) · **Status:** DONE, awaiting CTO smoke

Relaxes the residency active-uniqueness index from **single-active-per-user** to
**active-per-(user, apartment)**, enabling CTO-approved concurrent multi-residency. Pure index
change — NO data migration, NO business-logic change (P1 already made every consumer multi-safe).

## Prerequisite — P1 confirmed landed (sweep-before-relax honored)

Verified in code before touching the index: no production path calls the singular
`findActiveByUserId` (it is retained `@deprecated`, referenced only in javadoc). Every HIGH/MED
consumer uses a multi-safe query:
- `/residents/me` → `findAllActiveByUserId` (`ResidentServiceImpl:105`)
- 5 ticket guards + vehicle owns-check → `existsActiveByUserIdAndApartmentId`
- ticket mine-scope/redaction → `findActiveApartmentIdsByUserId` (`TicketServiceImpl:223,965`)
- announcement feed → `findAllActiveByUserId` union (`AnnouncementServiceImpl:113`)
- amenity primary-or-latest → `findAllActiveByUserId` first (`AmenityServiceImpl:299`)

DECISIONS.md "Residency lifecycle — CTO ruling" records P1 DONE; hard ordering (P1 before P2) satisfied.

## Pre-flight data check (read-only, BEFORE writing the migration)

```
SELECT user_id, apartment_id, COUNT(*) FROM residents
WHERE move_out_date IS NULL GROUP BY user_id, apartment_id HAVING COUNT(*) > 1;
```
**Result: EMPTY** (no duplicate active (user, apartment) pair on dev `gemek`). The new unique index
builds without violation. No data was deleted or mutated.

## Before → after live index def (dev `gemek`)

| State | `indexdef` |
|---|---|
| BEFORE | `CREATE UNIQUE INDEX uq_residents_active_user ON public.residents USING btree (user_id) WHERE (move_out_date IS NULL)` |
| AFTER  | `CREATE UNIQUE INDEX uq_residents_active_user ON public.residents USING btree (user_id, apartment_id) WHERE (move_out_date IS NULL)` |

One line: `(user_id) WHERE move_out_date IS NULL` → `(user_id, apartment_id) WHERE move_out_date IS NULL`.
Same index **name** retained.

## Migration

- **File:** `backend/src/main/resources/db/migration/V20__relax_uq_residents_active_user_per_apartment.sql`
- Content: `DROP INDEX uq_residents_active_user;` then
  `CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id, apartment_id) WHERE move_out_date IS NULL;`
- INDEX-only: no table/column drop, no INSERT/UPDATE/DELETE. Header comments explain the relaxation
  and cite the P1 prerequisite + DECISIONS phased plan.
- **Applied to dev `gemek` via the normal pipeline** (`docker compose up -d --build backend` →
  Spring Boot Flyway migrate on boot). No `flyway clean`. Flyway log:
  `Migrating schema "public" to version "20 - relax uq residents active user per apartment"` →
  `Successfully applied 1 migration ... now at version v20`. `flyway_schema_history` V20 success=`t`.
- **DB-SCHEMA.sql** updated: index def + the stale comment
  (`a user may only be active in one apartment at a time` → `a user may be active in multiple
  apartments concurrently; uniqueness is per (user, apartment)`). `Resident.java` entity javadoc
  corrected the same way (it still asserted single-active).

## Multi-residency integration test (the payoff)

`backend/src/test/java/vn/vtit/gemek/integration/MultiResidencyIntegrationTest.java` — runs against
the isolated test DB (`gemek_test`, Flyway clean+migrate picks up V20 automatically), `@Transactional`
rollback. Constructs a genuine 2-active state the NORMAL way (second `residents` row persisted through
the real repository, hitting the live partial unique index — not bypassing it). 5 tests:

1. **Two active residencies in two different apartments persist** (the INSERT that previously violated
   the index now succeeds; `findAllActiveByUserId` returns 2) **AND the SAME (user, apartment) pair is
   still rejected** (duplicate active row → `DataIntegrityViolationException`, observed Postgres
   `23505` on key `(user_id, apartment_id)` — proves the relaxed index still guards the real invariant).
2. `GET /residents/me` returns BOTH residencies (apartment A and B present).
3. Ticket per-context: create allowed for each resided apartment, **denied (403) for a third
   non-resided apartment**; default RESIDENT list (household scope) includes both apartments' tickets.
4. Announcement feed: an ALL-scope published announcement appears **exactly once** for the 2-residency user.
5. Amenity booking resolves primary-or-latest residency deterministically and does **not** throw
   (`201 APPROVED`).

**Result: 5/5 green.** Full backend suite **371/371 green, 0 failures / 0 errors across 54 classes**
(366 P1 baseline + 5 new). Raw: `reports/p2-suite.raw.txt` (per-class surefire reports authoritative).

## /code-review

**Verdict: APPROVED — no Must-fix.** (code-reviewer agent, scoped to the 4 P2 files.)

- Migration DDL verified correct: same index name, `(user_id)` → `(user_id, apartment_id)`, partial
  `WHERE move_out_date IS NULL` preserved, **no data DML**, matches V4 origin + DB-SCHEMA.sql.
- Test correctly constructs the 2-active state through real persistence (hits the live index, not
  bypassed). Test 1 isolation correct: the constraint-violating `saveAndFlush` is the LAST statement;
  `@Transactional` rolls back the method. All 5 assertions judged strong / non-tautological (Tests 2 & 4
  parse structurally; Test 1 proves both directions of the relaxed invariant).
- No wildcard imports; `Resident.java` change correctly javadoc-only.

**Findings dispositions:**
- ⚠️ `DROP INDEX IF EXISTS` (operational robustness) — **NOT applied, deliberately.** V20 was already
  applied to dev `gemek` and is recorded in `flyway_schema_history` with a checksum; editing the file
  now would cause a Flyway checksum mismatch and fail the next boot / CTO smoke. The flyway rule
  "never modify a migration that has already run" governs. The unguarded `DROP` is safe in the normal
  pipeline because V4 always creates the index first. Logged in DECISIONS.
- 💡 Optional test nits (fully-qualified `Set`/`HashSet`; Test 3 `mine` substring vs structural parse;
  Test 5 asserts no-throw + `201 APPROVED` but not *which* residency was selected) — **accepted as-is**:
  test-only, non-blocking, no behavioral impact. Test 5 proves the P2 claim that matters (no
  `NonUniqueResultException` under 2 active rows). Not re-touched to avoid an unnecessary suite re-run.
