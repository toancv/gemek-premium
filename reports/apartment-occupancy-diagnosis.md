# Apartment Occupancy Diagnosis — "vacant despite residents" bug

**Date:** 2026-06-22  **Branch:** `deploy/local`  **HEAD:** `c49c179`
**Tree state:** clean (only untracked `reports/*` — no modified/staged code)
**Mode:** READ-ONLY diagnosis. No code changed. No fix implemented.

---

## TL;DR

Occupancy is a **STORED** enum field (`apartments.status`) that is **never written by any resident path**. Adding/removing residents does NOT flip `status`. The field only ever changes on apartment create (→`AVAILABLE`) and on the admin manual edit endpoint. Result: every apartment that got residents the normal way still reads `AVAILABLE` → shows "còn trống / vacant".

**Root cause: (A) stored-field-not-synced.** Pre-existing, not a recent regression.

**Scope of corruption: 1612 of 1622 occupied apartments wrongly show AVAILABLE** (dev DB). A one-off data-fix migration **is needed** in addition to the logic fix.

**Bonus bug: occupancy surfaces disagree.** Dashboard reads the stored field (wrong); the resident-report reads a derived count (correct). Same concept, two implementations, two different answers.

---

## §1 — How occupancy is determined (per surface)

`apartments.status` is a stored PostgreSQL enum `apartment_status {AVAILABLE, OCCUPIED, MAINTENANCE}`, default `AVAILABLE`.
- Entity: `backend/.../apartment/entity/Apartment.java:79-82` (`status`, default `AVAILABLE`)
- Enum: `backend/.../apartment/entity/ApartmentStatus.java:12-22`

Three surfaces show occupancy, using **two different logics**:

| Surface | Code | Logic | Correct? |
|---|---|---|---|
| Apartment **list / detail** | `ApartmentMapper.java:34-52` → copies `apartment.status` raw | **(A) STORED** | ✗ wrong |
| **Dashboard** KPI (occupied/available/occupancyRate) | `ReportServiceImpl.buildApartmentStats()` :293-316 ← `ApartmentRepository.countByStatus()` :108-113 (`GROUP BY a.status`) | **(A) STORED** | ✗ wrong |
| **Resident report** (occupied / occupancyRate) | `ReportServiceImpl.getResidentReport()` :256-283 ← `ResidentRepository.getResidentDemographics()` :111-122 (`COUNT(DISTINCT r.apartment_id) WHERE move_out_date IS NULL`) | **(B) DERIVED** | ✓ correct |

So the two report surfaces compute the SAME concept (occupied apartments, occupancy rate) by different means and will disagree (see §5).

## §2 — Where `apartment.status` is SET (STORED case)

Every write of `status` in production code (grep `setStatus`/`status =`):

| Location | Writes | Trigger |
|---|---|---|
| `ApartmentServiceImpl.java:141` | `setStatus(AVAILABLE)` | apartment **create** (hardcoded) |
| `ApartmentServiceImpl.java:217` | `setStatus(request.status())` | apartment **admin update** (manual edit only) |

**Reads** of status: list query `ApartmentRepository.findAllWithFilters` :86-98 (filter), `countByStatus` :108-113 (dashboard), mapper copy.

**No resident path writes it.** `ResidentServiceImpl` injects `ApartmentRepository` but only calls `findById`/`existsById`:
- `createResident` (`ResidentServiceImpl.java:130-186`): loads apartment, attaches resident — **never** `apartment.setStatus(OCCUPIED)`.
- `moveOut` (`ResidentServiceImpl.java:252-294`): sets `resident.moveOutDate`, clears primary-contact, conditionally deactivates the linked user — **never** `apartment.setStatus(AVAILABLE)`.

➜ Status can only become `OCCUPIED` if an admin manually edits the apartment. Normal resident add/move-in/move-out leaves it at `AVAILABLE` forever. **This is the root cause.**

## §3 — DERIVED predicate (resident-report path)

`getResidentDemographics` (`ResidentRepository.java:111-122`) is correct:
`COUNT(DISTINCT r.apartment_id) ... FROM residents r JOIN apartments a ... WHERE r.move_out_date IS NULL`. Properly counts apartments with ≥1 active resident. No off-by-error. (The bug is that only ONE surface uses this; the others use the stored field.)

## §4 — Pre-existing vs recent regression

**Pre-existing.** The recent sprint work (commits `3eb1f9b`, `6d53eeb`, `ca9f2e9` — resident final move-out) touches `resident.moveOutDate` and `user.active` only; it never referenced `apartment.status`. The stored field was never synced from day one of the resident module. "Occupied apartment shows vacant" is an original design flaw (A), not introduced this sprint.

## §5 — Scope of data corruption (dev DB, port 5433 `gemek`)

```
total_apartments                          2269
status_AVAILABLE                          2259
status_OCCUPIED                             10
apartments_with_active_residents (truth)  1622
WRONG: status=AVAILABLE but ≥1 active res 1612   ← apartments shown vacant despite residents
WRONG: status=OCCUPIED but 0 active res      0
```

**1612 of 1622 truly-occupied apartments wrongly show AVAILABLE** (~99%). Only 10 apartments are correctly `OCCUPIED` (the ones an admin happened to manually edit). Inverse error (occupied-but-empty) = 0.

**Surface disagreement, same DB:** Dashboard (stored) reports **10 occupied / occupancyRate ≈ 0.4%**. Resident-report (derived) reports **1622 occupied / occupancyRate ≈ 71.5%**. Two screens, same metric, wildly different numbers.

---

## Conclusion

- **Root cause: (A) STORED field not synced.** `apartments.status` is authoritative for the list + dashboard surfaces but no resident create/move-out path ever writes it. Exact locations: writes only at `ApartmentServiceImpl.java:141,217`; resident paths `ResidentServiceImpl.java:130-186` (create) and `:252-294` (move-out) omit the status update.
- **Secondary bug: surfaces disagree.** Three occupancy surfaces, two logics (stored vs derived) — `ReportServiceImpl.buildApartmentStats` (stored) vs `getResidentReport`/`getResidentDemographics` (derived). Even after fixing A, leaving two logics invites future drift.

## Fix direction (NOT implemented — awaiting CTO ruling)

- **Option (i) — make occupancy DERIVED everywhere (recommended).** Drop reliance on the stored field for occupancy; compute occupied = "has ≥1 resident with `move_out_date IS NULL`" in all three surfaces (list/detail, dashboard, resident-report). The resident-report query (§3) already does this correctly and is the model. **Trade-off:** small per-query cost (a join/exists or correlated count); a `status` column kept only for the genuinely-manual `MAINTENANCE` state, or dropped. **Cannot desync — eliminates the bug class.** No data-fix migration needed for the AVAILABLE/OCCUPIED axis (it stops being stored).
- **Option (ii) — keep the stored field, sync it.** Set `status` on every resident create (→`OCCUPIED`) and on move-out (→`AVAILABLE` when the last active resident leaves), transactionally with the resident write. **Plus a one-off data-fix migration** to correct the 1612 corrupted rows. **Trade-off:** more write paths to keep correct forever (any future bulk import / direct insert re-introduces drift); must guard the OCCUPIED↔AVAILABLE transition vs the manual MAINTENANCE state so sync doesn't clobber it.

- **Data-fix migration needed?** **(ii) → YES** (1612 rows: `UPDATE apartments SET status='OCCUPIED' WHERE status='AVAILABLE' AND EXISTS active resident`). **(i) → NO** for the occupancy axis.
- **Multiple surfaces disagree?** **YES** — dashboard/list (stored) vs resident-report (derived); must converge on one logic as part of the fix.
