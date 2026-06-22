# Residency-Lifecycle — Read-Only Investigation

**Date:** 2026-06-22 · **Type:** diagnose-only (no code/migration/test changed) · **Branch:** `deploy/local`
**Status:** awaiting CTO ruling on residency-lifecycle design.

Every schema fact is cited from the latest Flyway migration (`backend/src/main/resources/db/migration/V*.sql`, file:line) **and** confirmed against the live dev DB (container `gemek-postgres`, `gemek` database, read-only `BEGIN TRANSACTION READ ONLY`). `DB-SCHEMA.sql` was NOT trusted. No migration after V4 alters the residents indexes (grep over V5–V19 = no `DROP`/`ALTER` of `uq_residents_active_user`).

---

## ⚠️ HEADLINE — DOMAIN MODEL ↔ SCHEMA CONTRADICTION

The CTO-approved domain model (`DECISIONS.md:884-886`) states a user may hold **multiple concurrent active residencies** ("living in 2+ apartments at once (confirmed allowed)") and claims `uq_residents_active_user` enforces only "one active row per **(user, apartment)**".

**The actual index does NOT match that claim.** `uq_residents_active_user` is partial-unique on **`user_id` ALONE** (not `(user_id, apartment_id)`). The current DB therefore **forbids** concurrent multi-residency entirely — at most ONE active residency per user across ALL apartments. The documented model and the running schema disagree. This frames every question below and is the first thing the design session must reconcile.

---

## A. ACTIVE-RESIDENCY UNIQUENESS

**Current DDL (migration):**
`backend/src/main/resources/db/migration/V4__create_residents_vehicles.sql:22`
```sql
CREATE UNIQUE INDEX uq_residents_active_user ON residents (user_id) WHERE move_out_date IS NULL;
```
Partial unique on `user_id` only. No later migration alters it (V5–V19 grep clean). Entity Javadoc agrees: `Resident.java:36-38` — "At most one resident per user may be active at any time".

**Live dev DB confirmation** (`pg_indexes`, read-only):
```
uq_residents_active_user | CREATE UNIQUE INDEX uq_residents_active_user ON public.residents USING btree (user_id) WHERE (move_out_date IS NULL)
```
Plus integrity check (live):
```
max_active_residencies_per_user = 1
active_residents_total = 1644 | distinct_active_users = 1644   (1:1 — no user has >1 active row today)
```

**Does the current DB ALLOW one user 2+ active residencies in different apartments simultaneously?**
**NO.** The partial unique key on `user_id` (where `move_out_date IS NULL`) rejects a second active row for the same user regardless of apartment. A move-in/return INSERT that adds a 2nd active row for an existing user **will throw a unique-constraint violation today**.

**COST of changing `(user_id) WHERE move_out_date IS NULL` → `(user_id, apartment_id) WHERE move_out_date IS NULL`** (i.e. enabling concurrent multi-residency): every "the single active residency of a user" assumption breaks or silently picks one arbitrary row. Call sites that assume singular (all personally verified, file:line):

| Call site | Code | Singular assumption | Risk |
|---|---|---|---|
| `ResidentRepository.java:91` | `Optional<Resident> findActiveByUserId(...)` | **returns `Optional` — would silently get 2 rows → `NonUniqueResultException`** | **HIGH** |
| `ResidentServiceImpl.java:103` (`getMyResident`) → `/residents/me` | `findActiveByUserId(userId).orElseThrow(...)` | returns "THE" active residency | **HIGH** |
| `VehicleServiceImpl.java:259-260` (`verifyResidentOwnsApartment`) | `findActiveByUserId(principalId).map(r -> r.getApartment().getId().equals(apartmentId))` | owns-check sees only one apartment | MED |
| `TicketServiceImpl.java:219-220` | `findActiveByUserId(...).map(r -> r.getApartment().getId())` (mine-filter) | "my apartment" singular | MED |
| `TicketServiceImpl.java:360-363, 607-610, 873-876, 906-909, 933-935, 970-975` | `findActiveByUserId(...)` then `.getApartment().getId()` equality (visibility/ownership guards) | 7 sites, all singular | MED/HIGH (most pervasive) |
| `AmenityServiceImpl.java:299-304` and `:392` (booking) | `findActiveByUserId(...).orElseThrow → resident.getApartment()` | booking charged to the one apartment | MED |
| `AnnouncementServiceImpl.java:109-115` (resident feed) | `findActiveByUserId(...) → resident.getApartment()` | feed scoped to one apartment | MED |

`existsActiveByUserId` (`ResidentRepository.java:99-104`) and `findActiveByUserIdAndApartmentId` (`:128-136`) are multi-residency-safe by construction (COUNT>0 / explicit apartment param) and would NOT break.

> The biggest single hazard: `findActiveByUserId` returns `Optional` via a `@Query` with no `LIMIT`. Under multi-residency it would throw `NonUniqueResultException` at runtime, not return one row. Changing the index without converting these to list-returning queries breaks `/residents/me` and all 7 ticket guards.

---

## B. `residents` vs `resident_history`

**`residents`** (`V4:4-23`) — columns: `id, user_id, apartment_id, type, move_in_date, move_out_date, is_primary_contact, notes, created_at, updated_at` (+ `created_by, updated_by` added `V17:40`). It IS the **residency table with soft-delete history**: a move-out sets `move_out_date` and **retains the row** (`ResidentServiceImpl.moveOut:265`, no delete). Rows are never physically deleted.

**`resident_history`** (`V4:25-42`) — columns: `id, apartment_id, user_id, type, event, event_date, changed_by_user_id, notes, created_at`. A **separate append-only event log** keyed by user+apartment. Entity `ResidentHistory.java`. Written ONLY via `ResidentServiceImpl.appendHistory(...)` (`:460-480`, single `historyRepository.save`).

**Write paths (every one runs inside the same `@Transactional` as its `residents` write):**

| Operation | `residents` write | `resident_history` write (event) | Same tx? |
|---|---|---|---|
| `createResident` (`:127-190`) | `save` new active row (`:177`) | `MOVED_IN` (`:179`); `PRIMARY_CONTACT_SET` if primary (`:181`) | YES (`@Transactional :128`) |
| `updateResident` (`:213-247`) | `save` (`:243`) | `TYPE_CHANGED` (`:226`) and/or `PRIMARY_CONTACT_SET` (`:233`) when changed | YES (`:214`) |
| `moveOut` (`:252-294`) | `save` w/ `move_out_date` (`:273`) | `MOVED_OUT` (`:275`) | YES (`:253`) |

**Verdict:** **Complementary, not redundant.** `residents` = current+historical residency rows (state, soft-deleted). `resident_history` = immutable audit trail of lifecycle events (MOVED_IN / MOVED_OUT / TYPE_CHANGED / PRIMARY_CONTACT_SET) with actor (`changed_by_user_id`). History is the source of truth for "what happened when / by whom"; `residents` is the source of truth for "current residency state". Both are actively read: `getResidentHistory` (`:300-311`, by user) and `getApartmentHistory` (`:317-329`, by apartment). Neither is unused.

---

## C. MOVE-IN / RETURN FLOW — does it exist?

**Does NOT exist. Confirmed against code (NOT assumed).** No endpoint/service reuses an existing user + adds a residency + reactivates a disabled account. The only residency-creating path is `POST /api/residents` → `createResident`.

**Current `createResident` behavior** (`ResidentServiceImpl.java:127-190`):
- ALWAYS constructs a **NEW** user: `User user = new User();` (`:150`), `userRepository.save(user)` (`:158`). No branch looks up an existing user by phone for reuse.
- If the phone already belongs to any existing user (incl. a moved-out / disabled one): `if (userRepository.existsByPhone(normalizedPhone)) throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS, ...)` (`:136-138`). The operator is **hard-blocked** here.
- Account always created `setActive(true)` (`:157`); no reactivation path exists.

This exactly matches the DECISIONS.md backlog statement ("NOT STARTED", `DECISIONS.md:917-933`): a returning resident whose phone is on a moved-out row cannot be re-registered, and there is no reuse-and-reactivate flow. API-SPEC `POST /api/residents` lists `409 PHONE_ALREADY_EXISTS` (`docs/API-SPEC.md:582`).

---

## D. PHONE AS IDENTITY

**Uniqueness — enforced BOTH at DB and app level:**
- DB: `V12__phone_as_login.sql:11` (`ALTER COLUMN phone SET NOT NULL`) + `:18` (`ADD CONSTRAINT uq_users_phone UNIQUE (phone)`). (In `V1:156` phone was nullable `VARCHAR(20)` with a plain index `V1:173`; V12 promoted it to the NOT NULL unique login key.)
- App: `userRepository.existsByPhone(normalizedPhone)` pre-check in `createResident` (`ResidentServiceImpl.java:136`) and in self-profile update with self-exclusion (`AuthServiceImpl.java:293`). Phone normalized via `PhoneUtils.normalize(...)` before every check/store.

**Live dev DB (read-only):**
```
users.phone — is_nullable = NO, type = character varying
users_total = 2082 | users_null_phone = 0 | duplicate-phone groups = 0
```
→ **"lookup by phone → exactly one user" is SAFE today** (0 null, 0 duplicate).

**Login resolves by phone:** `AuthServiceImpl.java:117-119` —
```java
String normalizedPhone = PhoneUtils.normalize(request.phone());
User user = userRepository.findByPhone(normalizedPhone)
        .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS, ...));
```
API-SPEC: "Phone is the login identifier" (`docs/API-SPEC.md:74`). Null/duplicate handling: `findByPhone` returns `Optional`; the `uq_users_phone` unique + NOT NULL constraints make >1 / null impossible, so no explicit null/duplicate branch is needed (a duplicate would surface as a `NonUniqueResultException`, but the constraint prevents it). Wrong/absent phone → `INVALID_CREDENTIALS` (`:127`).

---

## E. MOVE-OUT DEACTIVATION SCOPE

**`existsActiveByUserId`** (`ResidentRepository.java:99-104`):
```sql
SELECT COUNT(r) > 0 FROM Resident r WHERE r.user.id = :userId AND r.moveOutDate IS NULL
```
**Counts active residency across ANY apartment** for the user — NOT scoped by apartment. Correct under multi-residency: after `moveOut` sets `move_out_date` on the leaving row (`ResidentServiceImpl.java:265`), this answers "does the user still have any OTHER active residency anywhere?". Deactivation fires only when the answer is false (`:285-290`). Correct.

**Primary-contact clearing scope** — `ResidentServiceImpl.moveOut:268-270`:
```java
if (resident.isPrimaryContact()) {
    resident.setPrimaryContact(false);
}
```
Operates on the **single `resident` ROW being moved out only** (the entity loaded by `findById(id)` at `:257`). It is **per-residency / per-row**, NOT user-wide. So under multi-residency, moving out of apartment A would NOT touch the same user's primary-contact flag on apartment B. **The DECISIONS.md OPEN concern (`:907-910`) "verify the scope is per-residency" — verified: it already IS per-residency. No user-wide clearing bug here.** (Note: `clearPrimaryContactInApartment` at `:440-449` clears by apartment, used by create/update for the "one primary per apartment" rule — also correctly apartment-scoped, not user-scoped.)

---

## F. ONE-USER-ONE-APARTMENT HIDDEN ASSUMPTIONS

Audited for behavior if a user had 2 concurrent active residencies (which the DB currently forbids — see A). Risk = what would break IF the index were relaxed.

| Area | Key / evidence (file:line) | Keyed by | Breaks under multi-residency? | Risk |
|---|---|---|---|---|
| **`/residents/me`** | `ResidentServiceImpl.java:103` `findActiveByUserId(...).orElseThrow` (Optional, no LIMIT) | user | Returns "THE" residency; **`NonUniqueResultException` with 2 active rows** | **HIGH** |
| **Tickets** (ownership + visibility guards) | `TicketServiceImpl.java:219, 360, 607, 873, 906, 933, 970` all `findActiveByUserId(principalId)` → `.getApartment().getId()` equality | user→one apt | Resident with 2 apts only ever matches one; tickets for the other apt mis-scoped / wrongly forbidden; `NonUniqueResultException` risk | **HIGH** |
| **Amenity booking** | `AmenityServiceImpl.java:299-304`, `:392` `findActiveByUserId(...).orElseThrow → resident.getApartment()` | user→one apt | Booking always attributed to one apartment; can't book "as" the other | MED |
| **Announcement resident feed** | `AnnouncementServiceImpl.java:109-115` `findActiveByUserId(...) → resident.getApartment()` | user→one apt | Feed scoped to one apartment only; misses the other apt's block/floor announcements | MED |
| **Vehicles** | entity FKs `resident_id` + `apartment_id` (`V4:46-47`); resident owns-check `VehicleServiceImpl.java:259-260` `findActiveByUserId(...).map(r->r.getApartment()...)` | resident + apt; owns-check by user→one apt | Vehicle rows themselves are fine (FK to specific resident+apt); the RESIDENT self-service owns-check sees only one apt → could wrongly reject a vehicle op for the 2nd apt | MED |
| **Notifications** | `Notification` entity FK `user_id` only (no apartment column) — sub-audit; household dispatch resolves recipients per-apartment via `findActiveByApartmentId` (`ResidentServiceImpl.java:404`) | user (row); apartment (fan-out) | Notification rows are per-user (fine). Announcement dispatch recipient query `findRecipientUserIds`/`findRecipientUserIdsByScopeName` (`ResidentRepository.java:181-208`) joins active residents per scope and `DISTINCT u.id` — multi-residency-SAFE | LOW |
| **Parking** | assignment FK to `apartment_id` (sub-audit; `parking_slots`/`parking_assignments`, `V9`) | apartment | Assignments are apartment-scoped, not user→apartment; admin assigns to an apartment directly | LOW |
| **Billing / fees / contracts** | `Contract` linked to `contractor_id` only (sub-audit; `V6`) — no user/resident/apartment-resident link | contractor (building-level) | No per-resident charging exists; "which residency is charged" is N/A today | N/A |

> Vehicles/parking/notifications/contracts line numbers in this table outside the resident/ticket/amenity/announcement modules come from a sub-agent code locate (`parking`, `contract`, `notification` entities). `[TODO: verify]` the exact `Notification.java`, `ParkingAssignment.java`, `Contract.java` FK line numbers before relying on them in implementation — the resident/ticket/amenity/announcement/vehicle-owns-check sites in this report were personally read and verified.

---

## STEP-0 GROUND-TRUTH RECONCILIATION

- `git status`: working tree clean of tracked changes; only pre-existing untracked `reports/*` artifacts + `scripts/GenHash.java`. No modified tracked files.
- `git log` HEAD = `01a1c64 docs(context): residency lifecycle domain model + move-in backlog item` — matches DECISIONS.md `:865-933` (the model + backlog this investigation examines). PROGRESS.md top section ("Apartment status LOCKDOWN", `:3`) reflects the prior step; the residency commit was docs-only (DECISIONS + backlog). **No divergence between git and the docs.** Proceeded.

---

## OPEN QUESTIONS FOR CTO (nothing proposed as decided)

1. **Reconcile model vs schema (A).** The domain model says concurrent multi-residency is allowed; the live index forbids it. Which is authoritative — keep single-active-residency (and correct the DECISIONS.md wording), or relax the index to `(user_id, apartment_id)` and absorb the call-site cost in A/F?
2. If relaxing: `findActiveByUserId` (Optional, no LIMIT) and its ~11 singular consumers (`/residents/me`, 7 ticket guards, amenity, announcement, vehicle owns-check) need a defined "which residency" semantic — they will otherwise throw `NonUniqueResultException`.
3. **Move-in/return flow (C)** is genuinely absent; the `PHONE_ALREADY_EXISTS` block is the operator's current dead-end for returning residents. Design needed regardless of the multi-residency decision.
4. Primary-contact scoping (E) is already per-residency — the DECISIONS.md OPEN item can be closed as verified-correct.
