# Apartment Occupancy Fix — derive AVAILABLE/OCCUPIED, MAINTENANCE priority, converge 3 surfaces

**Date:** 2026-06-22  **Branch:** `deploy/local`
**Diagnosis:** `reports/apartment-occupancy-diagnosis.md` (root cause A — stored-not-synced).
**Mode:** implemented (TDD). Backend only. FE needs no change (response field name/type unchanged).

---

## The single shared rule (where it lives)

`OccupancyResolver.effective(ApartmentStatus stored, boolean hasActiveResident)`
(`backend/.../module/apartment/OccupancyResolver.java`) — the ONE place the rule is expressed:

```
MAINTENANCE if stored == MAINTENANCE          (stored, manual, priority — CTO ruling)
else OCCUPIED if hasActiveResident            (derived: ≥1 resident with move_out_date IS NULL)
else AVAILABLE
```

The single occupancy **fact** (active-resident presence) is the predicate `move_out_date IS NULL`,
expressed once in `ResidentRepository` (`findActiveByApartmentIdIn` for the list, `findOccupiedApartmentIds`
for the aggregates). Every surface routes status through `OccupancyResolver`; none re-derives the rule.
**Convention: `OCCUPIED` is derived-only — never a stored value** (documented in the resolver javadoc,
DECISIONS, and migration V19).

## Three surfaces converged on the rule

| Surface | Before (bug) | After |
|---|---|---|
| Apartment **list** (`ApartmentServiceImpl.listApartments`) | mapper copied stored `status` | builds page, batch-fetches active residents, sets status via `OccupancyResolver` |
| Apartment **detail** (`getApartmentDetail`) | mapper copied stored `status` | derives via `OccupancyResolver` from the already-fetched active-resident list (0 new queries) |
| **Dashboard** (`ReportServiceImpl.buildApartmentStats`) | `countByStatus` GROUP-BY on stored field | shared `computeOccupancy(null)` → resolver over `findIdAndStatus` + `findOccupiedApartmentIds` |
| **Resident report** (`getResidentReport`) | derived (correct) but a SEPARATE query | shared `computeOccupancy(blockId)` — SAME code as dashboard → numbers identical by construction |

Dashboard and resident-report now call the **same private `computeOccupancy`** method, so they cannot
diverge. The removed `ApartmentRepository.countByStatus` (read the never-synced stored field) is gone.

## N+1 approach (list)

The list returns up to a page of apartments. Active residents for the WHOLE page are fetched in **ONE**
batch query `ResidentRepository.findActiveByApartmentIdIn(pageIds)` and grouped into a
`Map<apartmentId, List<Resident>>`; occupancy AND primary-contact are both derived from that map.
**No per-apartment resident query.** Guarded by a unit test asserting `findActiveByApartmentIdIn` is
called exactly once and the per-row `findActiveByApartmentId` is never called during list.
(This also removed the pre-existing per-row primary-contact query.)
The dashboard/report aggregate loads one lightweight `[id, status]` projection (2269 rows) + one
occupied-id set per call — no per-row query.

## Normalize migration (10 rows)

`V19__normalize_apartment_occupied_status.sql`:
`UPDATE apartments SET status='AVAILABLE' WHERE status='OCCUPIED';`
The 10 manually-set OCCUPIED rows become AVAILABLE so the stored column only ever holds
`AVAILABLE`/`MAINTENANCE`. Their effective status recomputes to OCCUPIED automatically when they have
residents. **No 1612-row UPDATE** — derivation fixes those with no data change. The `OCCUPIED` enum value
is kept (still a valid response value). MAINTENANCE rows untouched.

## Convergence numbers (dev DB, 2269 apartments)

| Metric | Before (diagnosis) | After (derived) |
|---|---|---|
| Dashboard occupied / rate | 10 / ≈0.4% | **1622 / ≈71.5%** |
| Resident-report occupied / rate | 1622 / ≈71.5% | **1622 / ≈71.5%** |
| **Agree?** | **NO (10 vs 1622)** | **YES (1622 == 1622)** |

Derived breakdown now: OCCUPIED 1622, AVAILABLE 647, MAINTENANCE 0, total 2269.
The 1612 apartments that wrongly showed vacant now correctly show OCCUPIED on every surface.

## Tests (TDD, green)

- `OccupancyResolverTest` (5): AVAILABLE+resident→OCCUPIED; AVAILABLE+none→AVAILABLE;
  MAINTENANCE+resident→MAINTENANCE (priority); MAINTENANCE+none→MAINTENANCE; stray stored OCCUPIED re-derived.
- `ApartmentServiceImplTest` (+3): list derives OCCUPIED/AVAILABLE/MAINTENANCE in one batch (N+1 guard:
  batch called once, per-row never); detail active→OCCUPIED; detail MAINTENANCE-wins.
- `ReportServiceImplTest` (new, 2): dashboard derives with MAINTENANCE priority; dashboard==resident-report
  convergence (==2 in fixture).
- Full backend suite: see VERIFY below.

## Known limitation (deferred, recorded)

The apartment list **`?status=` query filter** still matches the *stored* value (`findAllWithFilters`).
Post-V19 the stored column holds only AVAILABLE/MAINTENANCE, so filtering `?status=OCCUPIED` returns
nothing and `?status=AVAILABLE` returns derived-occupied units too. Display (the reported bug) is fixed;
deriving the *filter* requires pushing occupancy into the paginated query (native CASE/EXISTS + count
query), which would duplicate the rule in SQL or break JPA pagination. Deferred as a follow-up; noted in
API-SPEC and DECISIONS.
