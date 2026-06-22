# P1 — `findActiveByUserId` Multi-Residency Sweep

**Date:** 2026-06-22 · **Branch:** `deploy/local` · **Type:** AUTH/PERMISSION gate · **Index:** NOT touched (stays single-active; relax is P2)

Goal: eliminate EVERY reliance on `findActiveByUserId`'s singular `Optional` return so that when P2 relaxes
`uq_residents_active_user` to `(user_id, apartment_id)`, no code path throws `NonUniqueResultException`. Per-surface
"which residency" semantics are CTO-ruled (not invented here).

## Re-verification vs §A (investigation report)

All 8 production call-sites re-grepped against current code — line numbers MATCH §A exactly:

| Site | File:line | §A match |
|---|---|---|
| `/residents/me` | `ResidentServiceImpl.java:103` | ✅ |
| ticket mine-scope (redaction) | `TicketServiceImpl.java:219` | ✅ |
| ticket guard (createTicket) | `TicketServiceImpl.java:360` | ✅ |
| ticket guard (uploadPhotos) | `TicketServiceImpl.java:607` | ✅ |
| ticket guard (enforceReadAccess) | `TicketServiceImpl.java:873` | ✅ |
| ticket guard (enforcePhotoAccess) | `TicketServiceImpl.java:906` | ✅ |
| ticket guard (isHouseholdMember) | `TicketServiceImpl.java:933` | ✅ |
| ticket scope (buildScopeSpec "mine" tab) | `TicketServiceImpl.java:970` | ✅ |
| amenity booking (createBooking) | `AmenityServiceImpl.java:299` | ✅ |
| amenity (listBookings IDOR scope) | `AmenityServiceImpl.java:392` | ✅ |
| announcement resident feed | `AnnouncementServiceImpl.java:109` | ✅ |
| vehicle owns-check | `VehicleServiceImpl.java:259` | ✅ |

### CORRECTIONS to §A assumptions (noted, NOT edited blind)

1. **`existsActiveByUserIdAndApartmentId` did NOT pre-exist.** The CTO semantic #2 and §A assume this multi-safe
   query "already exists". It does not. What exists is `existsActiveByUserId` (no apartment, `:99`) and
   `findActiveByUserIdAndApartmentId` (Optional, `:128`). **Resolution:** added a boolean COUNT>0
   `existsActiveByUserIdAndApartmentId(userId, apartmentId)` — the cleanest membership check (no entity hydration).

2. **`:970` (buildScopeSpec) is a LIST scope, not a single-ticket guard.** §A/CTO-#2 bucket `:970` among the "6
   guards" (PER-CONTEXT). But structurally `:970` builds the `mine`-tab list predicate (`apartment.id = X` over many
   rows) — it has NO single ticket to compare against, so PER-CONTEXT (`existsActiveByUserIdAndApartmentId`) is
   **structurally impossible** there. It matches CTO semantic **#3 (ALL, `apartmentId IN set`)** — same family as the
   `:219` redaction. **Resolution (NOT a deviation — applies the ruled semantic to verified code):** list surfaces
   `:219` + `:970` → ALL; the 5 single-ticket guards (`:360, :607, :873, :906, :933`, each comparing to a SPECIFIC
   ticket's apartment) → PER-CONTEXT. This is the only reading under which both ruled semantics fit the actual code.

## New repository methods (`ResidentRepository.java`)

- `List<Resident> findAllActiveByUserId(UUID)` — JOIN FETCH user+apartment+block, `ORDER BY primaryContact DESC,
  moveInDate DESC, id DESC`. Returns ALL active residencies. Used by `/residents/me` (map all) and amenity
  (take first = primary-or-latest, deterministic).
- `List<UUID> findActiveApartmentIdsByUserId(UUID)` — lightweight id projection, ONE query (N+1-safe). Used by ticket
  ALL surfaces (`:219` redaction, `:970` scope).
- `boolean existsActiveByUserIdAndApartmentId(UUID, UUID)` — COUNT>0 membership. Used by 5 ticket guards + vehicle.
- `findActiveByUserId` — kept, now carries an `@deprecated`-style Javadoc note (unsafe under multi-residency; do not
  use). NOT deleted in P1 (deletion is separate cleanup). No production code calls it after the sweep.

## Per-surface before → after

| # | Surface | Semantic | Before | After |
|---|---|---|---|---|
| 1 | `/residents/me` | ALL | `findActiveByUserId.orElseThrow → ResidentResponse`; empty=404 | `findAllActiveByUserId → List<ResidentResponse>`; empty=`[]` 200 |
| 2 | 5 ticket guards | PER-CONTEXT | load 1 residency, `.getApartment().getId().equals(ticketApt)` | `existsActiveByUserIdAndApartmentId(uid, ticketApt)` |
| 3 | ticket mine (`:219`,`:970`) | ALL | derive 1 apt, `equals`/`apt = X` | `findActiveApartmentIdsByUserId` set; `contains` / `apt IN set` |
| 4 | announcement feed | ALL DISTINCT | 1 apt → `findPublishedForApartment(block,floor)` | active-apt set → Specification union (ALL ∪ BLOCK∈blocks ∪ FLOOR∈(block,floor) pairs), `distinct` |
| 5 | vehicle owns-check | PER-CONTEXT | load 1 residency, `.getApartment().getId().equals(apt)` | `existsActiveByUserIdAndApartmentId(uid, apt)` |
| 6 | amenity (`:299`,`:392`) | SAFE TEMP `[PLANNED]` | `findActiveByUserId.orElseThrow → resident` | `findAllActiveByUserId` → primary-or-latest (first of ordered list); `[PLANNED]` comment |

Announcement pair-correctness: a FLOOR announcement must match ONLY when (targetBlock, targetFloor) equals one of the
user's OWN (block,floor) pairs — naive `floor IN floors AND block IN blocks` would cross-match (user in BlockA/fl3 +
BlockB/fl5 wrongly matching a FLOOR ann for BlockA/fl5). The Specification ORs one exact pair predicate per residency.
Single query (no per-apartment loop+concat) → building-wide/ALL announcements appear exactly once.

## Frontend (resident app)

Shape: **bare array `ResidentResponse[]`** (not `{residencies:[]}`). Rationale: `useMyResident()` returns `r.data`
raw, both consumers (`MyTicketsPage`, `MyVehiclesPage`) index a single object today — a bare array is the minimal
idiomatic change (`[0]` for the 1-residency common case, `.map`/`<select>` for 2+, `length===0` → neutral empty).

- 1 residency → behave as today (auto-use `[0]`).
- 2+ → apartment `<select>` in create-ticket / register-vehicle forms (drives `apartmentId`/`residentId`); Vietnamese labels.
- 0 → existing neutral empty state retained.

## Tests (TDD, 2-active-residency fixtures via mocked repo)

Unit-level with mocked repo (the DB index still forbids 2 active rows, so a 2-row state is constructed by stubbing the
new list/exists queries — documented choice; integration-level 2-row insert is impossible until P2 relaxes the index):

- `/residents/me` returns BOTH residencies; empty → `[]` not 404.
- ticket PER-CONTEXT: allows the matching apartment, forbids the other (exists true/false).
- ticket mine: returns tickets of BOTH apartments (apt set membership).
- announcement feed: NO duplicate announcement id for a 2-residency user (ALL ann appears once).
- vehicle owns-check PER-CONTEXT.
- amenity picks primary-or-latest deterministically.

## Tests — result

- New/updated unit + integration proofs all green. Full backend suite **366/366** (was 359; +7 net).
  Evidence: `reports/p1-unit-green.raw.txt` (42 swept-class tests), `reports/p1-suite-green.raw.txt` (full).
- **RED→GREEN demonstrated** for the announcement no-duplicate proof: with the RESIDENT feed temporarily
  reverted to the naive per-apartment loop+concat, `listAnnouncements_residentTwoApartments_noDuplicateAndSingleQuery`
  FAILS (the path bypasses the single union query). Evidence: `reports/p1-announcement-red.raw.txt` (RED) →
  restored → `reports/p1-postreview.raw.txt` (GREEN). The other surfaces' proofs are GREEN-on-new by construction
  (they stub the new list/exists queries the pre-sweep code never called; the old singular code could not satisfy them).

## /code-review (high effort — correctness + removed-behavior + cross-file + conventions)

Two independent reviewers (correctness/removed-behavior + java-convention/cross-file). **Backend verdict: clean —
no correctness bugs, single-residency behavior preserved.** Cross-file trace confirmed: `getMyResident` list shape
has no caller expecting the old object; `existsActiveByUserIdAndApartmentId(userId, apartmentId)` param order correct
at all 6 call sites; amenity `resolveActiveResidency` preserves prior ErrorCodes (createBooking=NOT_FOUND,
listBookings=FORBIDDEN); announcement Specification FLOOR pair-matching correct, `query.distinct(true)` does not
inflate the pagination count (no row-multiplying joins), ALL announcement cannot appear twice; JOIN FETCH returns a
List (no in-memory-pagination pitfall); no NPE (apartment is `nullable=false` + fetched).

Findings + resolution:
- **FE contract break (Must-fix)** — `MyTicketsPage`/`MyVehiclesPage` consumed `/residents/me` as a single object →
  array breaks them. **Resolved** in the resident-FE commit (array shape + apartment picker for 2+).
- **M1 import order (Should)** — `jakarta` import after `org.slf4j` (AnnouncementServiceImpl); `HashSet` between
  `EnumMap`/`EnumSet` (TicketServiceImpl). **Resolved** (reordered).
- **M3 consistency-test oracle (Should)** — `AnnouncementRecipientConsistencyTest.feedSees` still used the deprecated
  singular `findActiveByUserId` (single-residency oracle that would silently diverge from the union feed). **Resolved**:
  migrated to `findAllActiveByUserId` + `anyMatch` union (identical for single residency, faithful under multi).
- **M2 UUID string-concat in an error message (Optional, pre-existing)** — `"...: " + principalId` in
  `AmenityServiceImpl.createBooking`. **Not changed**: pre-existing, codebase-wide error-message idiom, out of P1
  scope ("do not change unrelated code"). Logged here.

## Index / migration

Index `uq_residents_active_user` (partial-unique on `user_id`) **NOT touched**. No migration in P1. Relaxing to
`(user_id, apartment_id)` is P2 (migration gate), only after this sweep is CTO-smoked.
