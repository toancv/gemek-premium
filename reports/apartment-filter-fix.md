# Apartment `?status=` filter — derive effective status (resolve filter/display mismatch)

**Date:** 2026-06-22  **Branch:** `deploy/local`
**Predecessor:** `reports/apartment-occupancy-fix.md` (the display fix that introduced the gap).
**Mode:** implemented (TDD, red→green). Backend only. Response shape unchanged → FE needs no change.

---

## The problem (CTO smoke)

The occupancy DISPLAY fix made list/detail/dashboard derive AVAILABLE/OCCUPIED from active
residents, but the list `?status=` filter still matched the **stored** column
(`a.status = COALESCE(:status, a.status)`). Post-V19 the stored column only holds
AVAILABLE/MAINTENANCE, so the filter actively contradicted the display:

- `?status=OCCUPIED` → empty (OCCUPIED is never stored).
- `?status=AVAILABLE` → returned occupied units too (stored AVAILABLE regardless of residents).

This was broken **by** the display fix — not a deferrable limitation.

## The fix — filter by EFFECTIVE (derived) status, in SQL

`ApartmentRepository.findAllByEffectiveStatus` (called via the `findAllWithFilters` default
method) applies the same rule the display uses:

| `?status=` | SQL predicate |
|---|---|
| `OCCUPIED`    | `a.status <> MAINTENANCE AND EXISTS (active resident: move_out_date IS NULL)` |
| `AVAILABLE`   | `a.status <> MAINTENANCE AND NOT EXISTS (active resident)` |
| `MAINTENANCE` | `a.status = MAINTENANCE` (priority — residents irrelevant) |
| none          | all (unchanged) |

The in-SQL effective-status predicate (the relevant `@Query` fragment):

```jpql
AND (
  :statusName IS NULL
  OR (:statusName = 'MAINTENANCE' AND a.status = :maintenance)
  OR (:statusName = 'OCCUPIED'  AND a.status <> :maintenance
        AND EXISTS (SELECT r FROM Resident r WHERE r.apartment.id = a.id AND r.moveOutDate IS NULL))
  OR (:statusName = 'AVAILABLE' AND a.status <> :maintenance
        AND NOT EXISTS (SELECT r FROM Resident r WHERE r.apartment.id = a.id AND r.moveOutDate IS NULL))
)
```

## Count-query consistency

Kept as a single `@Query`. Spring derives the count query from the SAME JPQL, so the count and
the row query apply the IDENTICAL effective-status WHERE clause — `total` can never disagree with
the returned rows. Locked by a test asserting `total == data.length` for every status.

## Single source of truth / drift prevention

The filter runs in SQL (for pagination) so it cannot call `OccupancyResolver` (Java). Instead it
re-expresses the SAME rule, and the two are bound together:

- `OccupancyResolver` javadoc cross-references the repository predicate; the repository javadoc
  cross-references `OccupancyResolver`. Both state the predicate MUST stay in lock-step.
- The **filter↔display agreement test** (`ApartmentStatusFilterIntegrationTest`) asserts each
  `?status=X` returns exactly the apartments whose displayed status is X, over fixtures of every
  shape: occupied / vacant / maintenance / maintenance+resident. It fails if SQL and resolver drift.

## Enum-typing gotcha (recorded)

A fully-qualified enum LITERAL in JPQL (`...ApartmentStatus.MAINTENANCE`) made Postgres reject the
query: `ERROR: type "apartmentstatus" does not exist` (Hibernate cast to the enum's lowercased
simple name, not the real `apartment_status` type). Fix: pass the requested status as its NAME
(string branch selector — no enum anchoring needed) and bind `MAINTENANCE` as an enum PARAMETER,
which Hibernate anchors against `a.status` and types correctly. Same class of fix as the
recipient-query default-method pattern already in `ResidentRepository`.

## Performance

One in-SQL query. The `EXISTS` subquery on `residents(move_out_date IS NULL)` is a correlated
subquery executed by Postgres in the single query — **not** a per-row N+1.

## Tests (TDD, green)

`ApartmentStatusFilterIntegrationTest` (5, `@SpringBootTest` + real Postgres), fixtures in one block
(OCC-1 occupied, AVL-1 vacant, MNT-1 maintenance, MNT-2 maintenance+resident):

1. `?status=OCCUPIED` → only OCC-1.
2. `?status=AVAILABLE` → only AVL-1; **excludes occupied** (the CTO bug — was returning 2).
3. `?status=MAINTENANCE` → MNT-1 + MNT-2 (incl. the one with a resident — priority).
4. no filter → all 4.
5. filter↔display agreement + count consistency: for each status, every returned row's displayed
   `status` == the filter AND `total` == row count.

Red first (pre-fix: AVAILABLE returned AVL-1 **and** OCC-1 → total 2). Green after the fix.
Existing `ApartmentServiceImplTest` (mock) and `ApartmentControllerTest` unchanged and green.

## Verify

- Full backend suite (`backend/mvnw.cmd test`): **358/358**, 0 failures, 0 errors.
- Dev DB (gemek, 2269 apartments) — effective-status counts via the IDENTICAL predicate the
  endpoint runs:

  | `?status=` | count |
  |---|---|
  | OCCUPIED    | **1622** |
  | AVAILABLE   | **647** |
  | MAINTENANCE | **0** |
  | **sum**     | **2269** = total |

  Matches the derived display breakdown in `reports/apartment-occupancy-fix.md` (OCCUPIED 1622,
  AVAILABLE 647, MAINTENANCE 0).

## Files

- `backend/.../apartment/repository/ApartmentRepository.java` — `findAllWithFilters` now a default
  method delegating to `findAllByEffectiveStatus` (effective-status `@Query`).
- `backend/.../apartment/ApartmentServiceImpl.java` — comment; passes the enum through unchanged.
- `backend/.../apartment/OccupancyResolver.java` — javadoc cross-reference to the SQL predicate.
- `backend/.../apartment/ApartmentStatusFilterIntegrationTest.java` — new (5 tests).
- `docs/API-SPEC.md` — removed the "deferred filter" caveat; documents derived filter.
- `DECISIONS.md` — deferred entry marked RESOLVED + new dated decision.
