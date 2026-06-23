# P3 — Place-Resident Flow (move-in / return / add-concurrent, keyed by phone)

Residency-lifecycle **P3**. Makes concurrent multi-residency creatable through the product.
Depends on P2 (relaxed index `uq_residents_active_user (user_id, apartment_id) WHERE move_out_date IS NULL`,
V20, confirmed live). AUTH gate. CTO smoke at end.

## CTO rulings honored
- ONE smart endpoint (Phương án 1) — server branches on phone (new vs returning vs add-concurrent).
- ADMIN-only for lookup AND place (same `hasRole('ADMIN')` as current createResident; NOT widened).
- Reactivate = `enabled = true` ONLY (no role/password/force-reset touch). `[hoãn]` note logged.
- Identity NOT mutated on reuse — existing user's name/dob reused as-is; place-resident never writes identity fields.
- Two-step UX; server NEVER trusts step 1 (re-resolves phone independently, ignores any client userId).

## Backend

### 1. LOOKUP — `GET /api/residents/lookup?phone=<phone>[&apartmentId=<uuid>]` (ADMIN, read-only)
GET chosen (RESTful read; consistent with existing `?search=` param endpoints; ADMIN-gated so phone-in-querystring
is acceptable). Server normalizes phone, resolves user. Returns `ResidentLookupResponse`:
```
{ status: NEW|ACTIVE_ELSEWHERE|MOVED_OUT|ALREADY_HERE,
  displayName: string|null,
  activeApartments: [ { id, unitNumber, blockName } ] }
```
Status resolution:
- phone not found → `NEW` (displayName null, empty list).
- found, `apartmentId` given AND user active in it → `ALREADY_HERE`.
- found, ≥1 active residency (elsewhere) → `ACTIVE_ELSEWHERE` (+ active apartment list).
- found, no active residency / account disabled → `MOVED_OUT`.
PII discipline: ADMIN-only; returns ONLY display name + active-apartment identifiers — no phone/email/dob/password/audit.

### 2. PLACE — extend `POST /api/residents` (ADMIN). New field `confirmReuse: boolean` (default false).
Server self-resolves phone (NO client userId accepted → IDOR-safe, identity server-derived):
- phone NOT found (NEW) → create user + residency (today's behavior). `confirmReuse` irrelevant.
- phone found, active in TARGET apartment → 409 `ALREADY_ACTIVE_IN_APARTMENT`, VN "Cư dân này đang ở căn hộ này rồi."
- phone found, NOT active in target:
  - `confirmReuse != true` → 409 `REUSE_CONFIRMATION_REQUIRED`, body carries matched user (`matched`: the lookup DTO).
    Nothing created.
  - `confirmReuse == true` → REUSE existing user (identity untouched); if account disabled → `enabled = true`
    (reactivate, enabled-only); insert new `residents` row for target apartment; append MOVED_IN history
    (actor = admin); household notify. ONE transaction.
- OLD `PHONE_ALREADY_EXISTS` hard block REMOVED — no longer reachable from this flow.
- Concurrent multi (active elsewhere + confirmReuse) succeeds (relaxed index). Same (user, apartment) twice still
  blocked — surfaces as `ALREADY_ACTIVE_IN_APARTMENT` (explicit pre-check), never reaching the index.

### Conditional validation (decision, logged in DECISIONS)
`fullName` / `password` / `dateOfBirth` bean-validation constraints (`@NotBlank`/`@Pattern`/`@NotNull`) REMOVED from
`CreateResidentRequest` — they are required ONLY for the NEW branch, which bean validation cannot detect (needs a DB
phone lookup). The service enforces them for NEW (presence + password complexity → `VALIDATION_ERROR` 400, preserving
the existing createResident weak-password contract). Always-required fields (phone, apartmentId, type, moveInDate)
keep their bean constraints. Reuse path ignores identity fields entirely.

### Error contract
New `ErrorCode`s: `REUSE_CONFIRMATION_REQUIRED` (409), `ALREADY_ACTIVE_IN_APARTMENT` (409).
`ReuseConfirmationRequiredException extends AppException` carries the matched `ResidentLookupResponse`; a dedicated
`@ExceptionHandler` emits the standard error body PLUS `matched`. `ALREADY_ACTIVE_IN_APARTMENT` uses plain AppException.

### Transaction + audit
Reuse path: residents row + resident_history MOVED_IN + (if needed) `enabled=true`, all in one `@Transactional`.
created_by via Spring Data auditing; history actor = acting admin (principalId). Multi-residency-safe queries only
(`findByPhone`, `existsActiveByUserIdAndApartmentId`, `findAllActiveByUserId`) — no deprecated `findActiveByUserId`.

## Frontend (admin :80) — two-step place UI
Add-resident modal leads with PHONE input + "Kiểm tra" button → lookup. On result:
- `NEW` → full new-resident form (as today) → submit creates user+residency.
- `ACTIVE_ELSEWHERE` / `MOVED_OUT` → confirm popup with matched name + active apartments → on confirm submit
  `confirmReuse:true` (identity fields NOT shown/editable in reuse path).
- `ALREADY_HERE` (target apt chosen) → inline VN block.
Errors inline VN via `getVnErrorMessage` (+ new keys); success via `meta.successMessage` (admin top-right toast);
refetch `['residents']`. Minimal, correct — no redesign.

## API-SPEC (same phase, docs commit)
Add lookup endpoint; update POST /residents contract (phone-first, `confirmReuse`, new statuses/errors, removal of
PHONE_ALREADY_EXISTS dead-end, branch behavior).

## Tests (gemek_test, isolated)
NEW; RETURNING (disabled→reactivate, new residency, history, identity reused/request ignored); ADD-CONCURRENT
(2nd active residency in different apartment, account stays enabled, BOTH active); confirmReuse=false→confirmation-
required, nothing created; ALREADY_ACTIVE same apartment→409 nothing created; IDOR (client cannot force identity);
lookup status + minimal PII per case. Unit `ResidentServiceImplTest` phone-dup case rewritten (old PHONE_ALREADY_EXISTS
block removed).

## Commit groups
`test(...)`, `feat(...)` BE, `feat(...)` admin FE, `docs(...)` API-SPEC, `docs(context)` PROGRESS+report+DECISIONS.
Suite GREEN + `git status` clean before each; `/code-review`; STOP for CTO smoke.
