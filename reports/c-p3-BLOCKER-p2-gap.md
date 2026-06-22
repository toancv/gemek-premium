# ⛔ BLOCKER — P3 cannot land: P2 route-guard audit (STEP B) never shipped

**Date:** 2026-06-17
**Phase:** Backlog (c) P3 (admit TECHNICIAN to admin portal allowed-set)
**Status:** HALTED before any code landed. Working tree clean (P3 edit reverted).
**Decision needed from:** CTO.

---

## TL;DR

The P3 task brief assumed **P2 is DONE** — specifically that admin route guards,
`homePathFor(TECHNICIAN)=/tickets`, and nav filtering "are all already in place,
waiting only for the store gate." **Ground truth says they are NOT.** The brief
conflated **P2.7** (the ticket-stats SLA card — which did ship) with **P2** (the
route-guard audit — which did NOT). Landing P3 now is explicitly **FORBIDDEN** by
CTO ruling 7 and would create a live data leak. Per the brief's own instruction
("if any of them seems to need changing, STOP and report a P2 gap rather than
patching"), P3 is halted.

---

## Evidence (ground truth, this session)

### 1. P2 route-guard STEP B was never applied

`reports/c-p2-route-audit.md` is **STEP A only**:
- Line 5: *"This report is STEP A (inventory + dashboard proposal). **No code edited yet** — awaiting CTO ruling on the dashboard/landing question."*
- §4 header: *"Planned STEP B changes (**NOT YET APPLIED** — pending CTO dashboard ruling)."*

`PROGRESS.md:267` (prior session's resume pointer) is unambiguous:
> *"NEXT after smoke = **P2** (audit & tighten `RequireRole` on ALL admin pages so a technician reaches no non-ticket page; dashboard ruling = Option 2) **→ then P3**. Order FIXED: **Do NOT start P3 until P2 lands.**"*

The `P2.5 / P2.6 / P2.7` commits are a **separate, CTO-approved scope addition**
(ticket-statistics card + overdue filter + drill-down), NOT the route-guard audit.

### 2. The route table still has the exact holes P2 was meant to close

`frontend/apps/admin/src/App.tsx`:

| Route | Line | `RequireRole`? |
|-------|------|----------------|
| `/dashboard` | 54 | **NONE — hole** (leaks contracts + occupancy to TECHNICIAN) |
| `/tickets` | 58 | **NONE** (intended TECHNICIAN surface, but should be explicitly guarded per §4) |
| `/tickets/:id` | 59 | **NONE** |
| index `/` | 53 | → `Navigate /dashboard` (not role-aware) |
| `*` catch-all | 69 | → `Navigate /dashboard` (not role-aware) |
| `RequireRole` fallback | 39 | → `Navigate /dashboard` (not role-aware) |
| `/residents`,`/users`,`/announcements`,`/vehicles` | 56,57,61,66 | `['ADMIN']` ✅ |
| `/apartments`,`/contractors`,`/reports` | 55,60,67 | `['ADMIN','BOARD_MEMBER']` ✅ |

### 3. `homePathFor` does not exist

`grep -rn "homePathFor" frontend/apps/admin/src` → **NONE FOUND**.
`LoginPage` and all redirect targets hardcode `/dashboard`. There is no role-aware
landing path; a logged-in TECHNICIAN would be sent to `/dashboard`.

### 4. DashboardPage leaks business data to TECHNICIAN

`DashboardPage.tsx` renders "Expiring contracts" (contract-expiry counts) and an
"Apartments" panel (total/occupied/available/occupancy rate) — explicitly scoped
**out** of the technician's "tickets only" surface by ruling 3 (`c-p2-route-audit.md` §3).

---

## Why landing P3 now is forbidden (not just risky)

**DECISIONS ruling 7 (2026-06-17), verbatim:**
> *"Implementation order is FIXED for safety: P1 → P2 audit & tighten RequireRole on ALL admin pages so a technician cannot reach any non-ticket page → only THEN P3 widen the admin allowed-set. **Widening the gate before per-page RequireRole is in place is FORBIDDEN — it would expose admin-only data to technicians** (investigation §3: tickets + dashboard routes currently have NO RequireRole)."*

Concrete failure if P3 ships against the current tree:
1. TECHNICIAN logs into admin :80 → succeeds (gate widened).
2. Login / index / catch-all / RequireRole-fallback all route to `/dashboard`.
3. `/dashboard` is unguarded → TECHNICIAN sees expiring-contract counts + building
   occupancy stats — a real cross-role data leak.

---

## What must happen first (P2 STEP B — owned by P2, NOT P3)

Per `c-p2-route-audit.md` §4 (CTO Option 2 recommended, dashboard ruling pending confirm):
1. Add `RequireRole ['ADMIN','BOARD_MEMBER','TECHNICIAN']` to `/tickets` + `/tickets/:id`.
2. Guard `/dashboard` to `['ADMIN','BOARD_MEMBER']` (Option 2).
3. Make index, `*`, deferred `/amenities`+`/parking` redirects, and the `RequireRole`
   fallback **role-aware** (`homePathFor(role)` → TECHNICIAN lands on `/tickets`).
4. Drop `TECHNICIAN` from the dashboard nav entry (keep only on `/tickets`).
5. Audit TicketsPage/TicketDetailPage for admin-only sub-actions to hide from TECHNICIAN.

**Only after P2 STEP B lands** is the P3 one-liner safe:
`ALLOWED_ROLES = ['ADMIN','BOARD_MEMBER','TECHNICIAN']` (admin authStore, both gates
covered by the single constant). That change is verified-trivial and was reverted
this session.

---

## Decision requested

1. **Confirm the dashboard/landing ruling** (Option 2 recommended in `c-p2-route-audit.md` §3)
   so P2 STEP B can be implemented.
2. **Approve running P2 STEP B before P3.** P3 stays a one-line gate widening once P2 lands.

No code shipped this session. Tree clean. Awaiting CTO.
