# API Specification — Apartment Management System

**Version:** 2.2
**Date:** 2026-06-18
**Base URL:** `https://{host}/api`

> **Reconciliation (2026-06-18, v2.2):** spec reconciled against the live controller layer (code = ground truth). 10 endpoints added (were in code, missing here), 10 contract mismatches corrected, 4 stale entries flagged for CTO ruling. Full diff: `reports/c-p5-apispec-reconciliation.md`.
**Auth:** `Authorization: Bearer <accessToken>` header on all endpoints unless marked **Public**.

---

## Global Conventions

### Standard Error Response

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2026-05-29T10:00:00Z",
  "path": "/api/tickets"
}
```

Common error codes:

| Code | HTTP | Meaning |
|------|------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `FORBIDDEN` | 403 | Valid token but insufficient role or resource ownership |
| `NOT_FOUND` | 404 | Resource does not exist |
| `VALIDATION_ERROR` | 400 | Request body or query param fails validation |
| `CONFLICT` | 409 | Duplicate or invalid state transition (generic fallback) |
| `AMENITY_NAME_EXISTS` | 409 | Amenity name already taken |
| `BOOKING_NOT_PENDING` | 409 | Approve/reject attempted on non-PENDING booking |
| `ANNOUNCEMENT_CONTENT_TOO_LONG` | 400 | Announcement Markdown body exceeds 20000 chars |
| `ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED` | 400 | Announcement body contains raw HTML (Markdown only) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `RATE_LIMITED` | 429 | Too many requests |

### Standard Paginated Response

```json
{
  "data": [],
  "page": 0,
  "size": 20,
  "total": 100,
  "totalPages": 5
}
```

Pagination query params (all list endpoints unless noted):
- `page` — 0-based page index (default: `0`)
- `size` — page size (default: `20`, max: `100`)
- `sort` — field name (default varies per endpoint, documented inline)
- `direction` — `asc` or `desc` (default: `desc`)

### Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Building manager — full control over all modules |
| `TECHNICIAN` | Internal maintenance / operations staff — works assigned tickets |
| `RESIDENT` | Apartment resident — personal portal |
| `BOARD_MEMBER` | Read-only access to reports and dashboard |

---

## 1. Auth

### POST /api/auth/login

**Auth:** Public
**Rate limit:** 10 req/min per IP
**Description:** Authenticate user and receive tokens. Phone is the login identifier.

**Phone normalization:** The server accepts any standard Vietnamese mobile format and normalizes to canonical `0xxxxxxxxx` (10 digits, leading 0, prefix 3–9) before lookup. Accepted input formats (all resolve to the same canonical form):
- `0901234567` — canonical, already correct
- `+84901234567` — international with `+`
- `84901234567` — international without `+`
- `+840901234567` — international with redundant leading `0`
- `096 246 4748` — spaces, dots, or hyphens stripped first

Invalid format → `400 VALIDATION_ERROR`.

Request:
```json
{
  "phone": "0901234567",
  "password": "string"
}
```

Response `200 OK`:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "phone": "0901234567",
    "fullName": "Nguyen Van A",
    "role": "RESIDENT",
    "avatarUrl": null
  }
}
```

Errors: `401 UNAUTHORIZED` (wrong credentials), `400 VALIDATION_ERROR` (missing field or invalid phone format)

---

### POST /api/auth/refresh

**Auth:** Public (refresh token in body)
**Rate limit:** 20 req/min per user
**Description:** Exchange a valid refresh token for a new access token.

Request:
```json
{
  "refreshToken": "eyJ..."
}
```

Response `200 OK`:
```json
{
  "accessToken": "eyJ...",
  "expiresIn": 900
}
```

Errors: `401 UNAUTHORIZED` (expired or invalid refresh token)

---

### POST /api/auth/logout

**Auth:** Any authenticated role
**Description:** Invalidate the current access token (JTI added to Redis blocklist). Client must discard both tokens.

Request: (no body)

Response `204 No Content`

---

### GET /api/auth/me

**Auth:** Any authenticated role
**Description:** Return the authenticated user's own profile.

Response `200 OK`:
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "role": "RESIDENT",
  "avatarUrl": "string|null",
  "isActive": true,
  "lastLoginAt": "2026-05-29T08:00:00Z"
}
```

---

### PUT /api/auth/me/password

**Auth:** Any authenticated role
**Description:** Change own password.

Request:
```json
{
  "currentPassword": "string",
  "newPassword": "string (min 8 chars, must include upper, lower, digit, special)"
}
```

Response `204 No Content`

Errors: `400 VALIDATION_ERROR` (weak password), `401 UNAUTHORIZED` (wrong current password)

---

### PUT /api/auth/me/profile

**Auth:** Any authenticated role
**Description:** Update the authenticated user's own profile. Identity is server-derived from the
principal — the body carries no id, and `role`/`isActive`/`password` cannot be changed here
(privilege-escalation guard; extra JSON keys are ignored at bind time). Phone/email uniqueness is
enforced excluding the caller's own row, so submitting the unchanged phone/email succeeds. The
access-token subject is the user UUID, so a phone/email change does not invalidate the session.

Request:
```json
{
  "fullName": "string (required, max 255)",
  "phone": "string (required, VN mobile 0xxxxxxxxx, max 20)",
  "email": "string|null (optional, valid email, max 255, blank treated as null)"
}
```

Response `200 OK`: same shape as `GET /api/auth/me` (the updated profile).

Errors: `400 VALIDATION_ERROR` (malformed phone/email), `409 PHONE_ALREADY_EXISTS` (phone used by
another user), `409 EMAIL_ALREADY_EXISTS` (email used by another user)

---

### PUT /api/auth/me/fcm-token

**Auth:** Any authenticated role
**Description:** Register or refresh the FCM device token for push notifications. Called on each app startup.

Request:
```json
{
  "fcmToken": "string"
}
```

Response `204 No Content`

---

## 2. Users

### GET /api/users

**Auth:** ADMIN
**Description:** List all users with optional filters.

Query params: `role`, `isActive` (bool), `search` (name or email substring)
Default sort: `createdAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "email": "string",
      "fullName": "string",
      "phone": "string|null",
      "role": "RESIDENT",
      "isActive": true,
      "createdAt": "ISO8601"
    }
  ],
  "page": 0, "size": 20, "total": 50, "totalPages": 3
}
```

---

### POST /api/users

**Auth:** ADMIN
**Description:** Create a new user account (staff or resident). Phone is the login identifier and is required; email is informational and optional.

Request:
```json
{
  "fullName": "string",
  "phone": "string (required — any VN mobile format, normalized server-side)",
  "email": "string|null (optional — must be valid format if provided; unique when present)",
  "role": "RESIDENT|TECHNICIAN|ADMIN|BOARD_MEMBER",
  "password": "string (min 8 chars, upper + lower + digit + special)"
}
```

Response `201 Created`:
```json
{
  "id": "uuid",
  "email": "string|null",
  "fullName": "string",
  "role": "string",
  "isActive": true,
  "createdAt": "ISO8601"
}
```

Errors: `409 PHONE_ALREADY_EXISTS` (phone already registered), `409 EMAIL_ALREADY_EXISTS` (email already registered), `400 VALIDATION_ERROR`

---

### GET /api/users/{id}

**Auth:** ADMIN

Response `200 OK` — same shape as list item.

---

### PUT /api/users/{id}

**Auth:** ADMIN
**Description:** Update user profile or role.

Request:
```json
{
  "fullName": "string",
  "phone": "string|null",
  "role": "string",
  "isActive": true
}
```

Response `200 OK` — updated user object.

---

### DELETE /api/users/{id}

**Auth:** ADMIN
**Description:** Deactivate user account (`is_active = false`). Cannot deactivate users with active resident assignments.

Response `204 No Content`

Errors: `409 CONFLICT` (user has active resident assignment)

---

### PUT /api/users/{id}/reset-password

**Auth:** ADMIN
**Description:** Admin forces a password reset for another user.

Request:
```json
{
  "newPassword": "string"
}
```

Response `204 No Content`

---

## 3. Blocks and Apartments

### GET /api/blocks

**Auth:** ADMIN, BOARD_MEMBER, TECHNICIAN

**Query params:** `search` (name substring, case-insensitive, optional), `page` (default 0), `size` (default 10, max 200), `sort` (name|createdAt, default name), `direction` (asc|desc, default asc)

Response `200 OK` — `PageResponse`:
```json
{
  "data": [
    { "id": "uuid", "name": "Block A", "description": "string|null" }
  ],
  "page": 0,
  "size": 10,
  "total": 42,
  "totalPages": 5
}
```

---

### POST /api/blocks

**Auth:** ADMIN

Request:
```json
{ "name": "Block A", "description": "string|null" }
```

Response `201 Created` — block object.

---

### PUT /api/blocks/{id}

**Auth:** ADMIN
Request: same shape as POST.
Response `200 OK` — updated block object.

---

### DELETE /api/blocks/{id}

**Auth:** ADMIN
Response `204 No Content`
Errors: `409 CONFLICT` (block has apartments)

---

### GET /api/apartments

**Auth:** ADMIN, BOARD_MEMBER
**Description:** List apartments. Supports filtering.

Query params: `blockId`, `floor`, `status`, `search` (unit number substring)
Default sort: `block name asc, floor asc, unitNumber asc`

**`status` semantics (occupancy is DERIVED).** The returned `status` is computed, not a raw stored value: `MAINTENANCE` when the apartment is manually flagged for maintenance (stored, takes priority); otherwise `OCCUPIED` when it has ≥1 active resident (`moveOutDate == null`), else `AVAILABLE`. `OCCUPIED` is therefore never stored — only `AVAILABLE`/`MAINTENANCE` are persisted (see migration V19). The same derivation backs apartment detail and the `occupied`/`occupancyRate` figures in `GET /api/reports/dashboard` and `GET /api/reports/residents`, so all four surfaces agree. Field name and enum values are unchanged. The `status` **query filter** matches the SAME effective (derived) status it displays: `?status=OCCUPIED` returns apartments with ≥1 active resident and not under maintenance; `?status=AVAILABLE` returns truly-vacant (no active resident, not maintenance); `?status=MAINTENANCE` returns maintenance-flagged units (incl. ones with residents — priority). The filter runs in SQL (EXISTS + MAINTENANCE binding) and Spring derives the count query from the same predicate, so the page `total` always matches the rows. The filter↔display agreement is locked by `ApartmentStatusFilterIntegrationTest`.

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "block": { "id": "uuid", "name": "Block A" },
      "floor": 3,
      "unitNumber": "A301",
      "areaSqm": 75.5,
      "status": "OCCUPIED",
      "primaryContact": {
        "id": "uuid",
        "fullName": "string",
        "type": "OWNER",
        "phone": "string"
      }
    }
  ],
  "page": 0, "size": 20, "total": 200, "totalPages": 10
}
```

---

### POST /api/apartments

**Auth:** ADMIN

Request:
```json
{
  "blockId": "uuid",
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "notes": "string|null"
}
```

Response `201 Created` — apartment object.
Errors: `409 CONFLICT` (unit number already exists in block)

---

### GET /api/apartments/{id}

**Auth:** ADMIN, BOARD_MEMBER, RESIDENT (own apartment only)
**Description:** Full apartment detail including current residents and registered vehicles.

Response `200 OK`:
```json
{
  "id": "uuid",
  "block": { "id": "uuid", "name": "Block A" },
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "status": "OCCUPIED",
  "notes": "string|null",
  "residents": [
    {
      "id": "uuid",
      "user": { "id": "uuid", "fullName": "string", "phone": "string", "email": "string" },
      "type": "OWNER",
      "moveInDate": "2024-01-01",
      "moveOutDate": null,
      "isPrimaryContact": true
    }
  ],
  "vehicles": [
    {
      "id": "uuid",
      "licensePlate": "51A-123.45",
      "type": "CAR",
      "brand": "Toyota",
      "color": "White",
      "isActive": true
    }
  ]
}
```

---

### PUT /api/apartments/{id}

**Auth:** ADMIN

Request:
```json
{
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "notes": "string|null"
}
```

**No `status` field.** Occupancy (`OCCUPIED`/`AVAILABLE`) is fully derived from active residents and `MAINTENANCE` has no set flow, so status is **not client-settable** via update — a supplied `status` is ignored/rejected (the field no longer exists on the DTO). The apartment keeps its stored status (`AVAILABLE` post-V19); the response `status` is the derived effective status. This closes the desync hole where an admin could store a status contradicting the derived display.

Response `200 OK` — updated apartment object.

---

### DELETE /api/apartments/{id}

**Auth:** ADMIN
Response `204 No Content`
Errors: `409 CONFLICT` (apartment has active residents)

---

## 4. Residents

### GET /api/residents/me

**Auth:** RESIDENT
**Description:** Return ALL of the authenticated resident's active residencies. Identity is derived
exclusively from the JWT principal — no user id is accepted as a param. A user may hold multiple
concurrent active residencies (multi-residency), so the response is an array.

Response `200 OK` — JSON **array** of `ResidentResponse` (each element same shape as
`GET /api/residents/{id}` detail), ordered primary-contact first, then latest move-in. An empty array
`[]` is returned when the caller has **no** active residency (a valid state, e.g. between move-out and a
return) — this is **NOT** a 404.

> **Contract change (residency-lifecycle P1, 2026-06-22):** previously returned a single
> `ResidentResponse` object and `404 NOT_FOUND` when the caller had no active residency. Now returns a
> list and `200 []`. Resident-app consumers updated accordingly.

Errors: none specific (an empty list, not an error, represents "no active residency").

---

### GET /api/residents

**Auth:** ADMIN
Query params: `apartmentId`, `type` (`OWNER`/`TENANT`), `isActive` (bool — filters by `move_out_date IS NULL`), `search` (optional string — case-insensitive substring match on resident user's `fullName` or `email`; blank/absent = no filter)
Default sort: `apartment.unitNumber asc, createdAt desc`

Response `200 OK` — paginated list of resident objects. Each resident includes `apartment.block.name`.

---

### GET /api/residents/lookup

**Auth:** ADMIN
**Description:** Step 1 of the place-resident flow. Resolves a phone (and optional target apartment) to a
branch status plus the minimum info for an admin to recognize the person. Read-only. The server still
re-resolves the phone at place-time and never trusts this lookup. PII discipline: returns ONLY the display
name + active-apartment identifiers — never phone, email, date of birth, password, or audit fields.

Query params: `phone` (required — any VN mobile format, normalized server-side), `apartmentId`
(optional — when supplied, enables the `ALREADY_HERE` status).

Response `200 OK`:
```json
{
  "status": "NEW | ACTIVE_ELSEWHERE | MOVED_OUT | ALREADY_HERE",
  "displayName": "string|null (null only for NEW)",
  "activeApartments": [ { "id": "uuid", "unitNumber": "A301", "blockName": "Block A" } ]
}
```
Status meaning:
- `NEW` — phone not found; placing will provision a new user + residency.
- `ACTIVE_ELSEWHERE` — user exists with ≥1 active residency (in other apartment(s)); `activeApartments` lists them.
- `MOVED_OUT` — user exists but has no active residency (moved out / disabled); `activeApartments` is `[]`.
- `ALREADY_HERE` — (only when `apartmentId` supplied) user already actively resides in that apartment.

Errors: `400 VALIDATION_ERROR` (malformed phone).

---

### POST /api/residents

**Auth:** ADMIN
**Description:** Place a resident into an apartment. The server branches on `phone` (the login identifier,
normalized server-side — see POST /api/auth/login for formats): an unused phone provisions a new user +
residency atomically; a phone that already belongs to an existing user REUSES that user (adding a residency,
reactivating a disabled account) once `confirmReuse=true`. This supports move-in, return, and concurrent
multi-residency. The server self-resolves the phone — no client-supplied `userId` is accepted (IDOR-safe);
on reuse, identity (name, dob, etc.) is taken from the existing user and is NEVER overwritten by request
values. Reactivation re-enables the account only (`is_active = true`) — role and password are untouched (a
returning user logs in with their old credentials).

Request:
```json
{
  "phone": "0901234567 (required — any VN mobile format, normalized server-side; the branch key)",
  "apartmentId": "uuid (required)",
  "type": "OWNER|TENANT (required)",
  "moveInDate": "2024-01-01 (required)",
  "confirmReuse": false,
  "isPrimaryContact": false,
  "notes": "string|null",

  "fullName": "Nguyen Van A (required for the NEW branch only; ignored on reuse)",
  "dateOfBirth": "1990-01-15 (required for the NEW branch only; ignored on reuse)",
  "password": "plaintext — BCrypt-hashed (required for the NEW branch only; ignored on reuse)",
  "email": "vana@example.com (optional, NEW branch only — unique when provided; ignored on reuse)"
}
```
> **Conditional fields:** `fullName`, `dateOfBirth`, `password` carry no bean-validation constraints; the
> service requires them (and enforces password complexity) only when the phone is new. The reuse branch
> ignores all identity fields.

Branch behavior:
- **NEW** (phone not found) → create user + residency. `201 Created` with the resident body below.
- **REUSE, not yet confirmed** (phone matches an existing user not active in the target apartment,
  `confirmReuse` omitted/false) → `409 REUSE_CONFIRMATION_REQUIRED`, nothing created. The body is the
  standard error shape PLUS a `matched` object (same shape as `GET /residents/lookup`) so the frontend can
  show a confirm popup, then re-submit with `confirmReuse: true`.
- **REUSE, confirmed** (`confirmReuse: true`) → reuse the existing user; reactivate (enabled-only) if
  disabled; add a new residency. `201 Created`. Adding a residency in a DIFFERENT apartment yields concurrent
  multi-residency (both active).
- **Already here** (existing user already active in the TARGET apartment) → `409
  ALREADY_ACTIVE_IN_APARTMENT` ("Cư dân này đang ở căn hộ này rồi."), nothing created.

Response `201 Created`:
```json
{
  "id": "uuid",
  "user": { "id": "uuid", "fullName": "string", "email": "string|null", "phone": "string", "dateOfBirth": "date" },
  "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
  "type": "OWNER",
  "moveInDate": "2024-01-01",
  "moveOutDate": null,
  "isPrimaryContact": false,
  "createdAt": "ISO8601"
}
```

`409 REUSE_CONFIRMATION_REQUIRED` body:
```json
{
  "error": "REUSE_CONFIRMATION_REQUIRED",
  "message": "string",
  "matched": { "status": "ACTIVE_ELSEWHERE|MOVED_OUT", "displayName": "string", "activeApartments": [ ... ] },
  "timestamp": "ISO8601",
  "path": "/api/residents"
}
```

Errors: `409 REUSE_CONFIRMATION_REQUIRED` (existing phone, reuse not confirmed — carries `matched`),
`409 ALREADY_ACTIVE_IN_APARTMENT` (existing user already active in the target apartment),
`409 EMAIL_ALREADY_EXISTS` (NEW branch, email already registered), `404 NOT_FOUND` (apartment not found),
`400 VALIDATION_ERROR` (malformed phone, or NEW branch missing fullName/password/dateOfBirth or weak password).

> **Contract change (residency-lifecycle P3, 2026-06-23):** the old `409 PHONE_ALREADY_EXISTS` hard block was
> REMOVED — an existing phone now drives the reuse branch (move-in / return / add-concurrent) instead of being
> rejected. New field `confirmReuse`; new statuses/errors `REUSE_CONFIRMATION_REQUIRED` and
> `ALREADY_ACTIVE_IN_APARTMENT`. Identity fields became NEW-branch-only.

---

### GET /api/residents/{id}

**Auth:** ADMIN, RESIDENT (own record only)

Response `200 OK` — full resident detail with apartment and user info.

---

### PUT /api/residents/{id}

**Auth:** ADMIN

Request:
```json
{
  "type": "OWNER|TENANT",
  "isPrimaryContact": true,
  "notes": "string|null"
}
```

Response `200 OK` — updated resident object.

---

### POST /api/residents/{id}/move-out

**Auth:** ADMIN
**Description:** Record a move-out event. Sets `move_out_date`, clears the resident's `is_primary_contact`, appends to `resident_history`. Additionally, in the same transaction, deactivates the resident's linked user account (`users.active = false`) **only if** the user has no remaining active residency (no other resident row with `move_out_date IS NULL`). A user who still lives in another apartment keeps their login. The whole operation is atomic — if deactivation fails, the move-out is rolled back.

Request:
```json
{
  "moveOutDate": "2025-12-31",
  "notes": "string|null"
}
```

Response `200 OK`

---

### GET /api/residents/{id}/history

**Auth:** ADMIN
**Description:** Return the change history for a specific resident record.

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "event": "MOVED_IN",
      "eventDate": "2024-01-01",
      "type": "OWNER",
      "changedBy": { "id": "uuid", "fullName": "string" },
      "notes": "string|null",
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### GET /api/apartments/{apartmentId}/history

**Auth:** ADMIN
**Description:** Full resident change history for an apartment (all residents, all time).

Response: same shape as above.

---

## 5. Vehicles

### GET /api/vehicles

**Auth:** ADMIN
Query params (as-built): `apartmentId`, `search` (case-insensitive substring on licensePlate, brand, model — null/blank = no filter)
Default sort: `apartment.unitNumber asc`, `licensePlate asc`

> **Reconciliation v2.2:** the controller binds only `apartmentId` + `search` (+ `page`/`size`). The prior `residentId`/`type`/`licensePlate`/`isActive` params were spec-only and are NOT implemented — removed.

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "resident": { "id": "uuid", "user": { "fullName": "string" } },
      "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
      "type": "CAR",
      "licensePlate": "51A-123.45",
      "brand": "Toyota",
      "model": "Camry",
      "color": "White",
      "isActive": true
    }
  ]
}
```

---

### POST /api/vehicles

**Auth:** ADMIN, RESIDENT (own apartment)

Request:
```json
{
  "residentId": "uuid",
  "apartmentId": "uuid",
  "type": "CAR|MOTORBIKE|BICYCLE|OTHER",
  "licensePlate": "51A-123.45",
  "brand": "string|null",
  "model": "string|null",
  "color": "string|null",
  "notes": "string|null"
}
```

Response `201 Created` — vehicle object.
Errors: `409 CONFLICT` (license plate already registered)

---

### GET /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)

Response `200 OK` — `VehicleResponse` (same shape as the vehicle list item).

---

### PUT /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)
Request: same as POST minus `residentId` and `apartmentId`.
Response `200 OK` — updated vehicle object.

---

### DELETE /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)
**Description:** Deactivate vehicle (`is_active = false`). Ends any active parking assignment for this vehicle.

Response `204 No Content`

---

## 6. Tickets

The ticket module handles ALL resident request types. The `category` field determines routing and contractor assignment eligibility.

**Category values:** `MAINTENANCE_REPAIR`, `COMPLAINT`, `ADMINISTRATIVE`, `SUGGESTION_FEEDBACK`, `OTHER`

**Status flow:** `NEW` → `ASSIGNED` → `IN_PROGRESS` → `DONE` (also: `CANCELLED`)

**Contractor assignment rule:** `assignedToContractorId` may only be set when `category = MAINTENANCE_REPAIR`. All other categories may only be assigned to internal staff.

---

### GET /api/tickets

**Auth:** ADMIN (all), TECHNICIAN (assigned to them + NEW/unassigned), RESIDENT (own apartment), BOARD_MEMBER (all, read-only)
**Description:** List tickets with rich filtering. Results are scoped by role automatically on the server side.

Query params:
- `category` — `MAINTENANCE_REPAIR` | `COMPLAINT` | `ADMINISTRATIVE` | `SUGGESTION_FEEDBACK` | `OTHER`
- `status` — `NEW` | `ASSIGNED` | `IN_PROGRESS` | `DONE` | `CANCELLED` — **may be repeated** for multi-value IN filtering (e.g. `?status=NEW&status=ASSIGNED`). Omit for no status restriction. An unrecognised value yields `400 VALIDATION_ERROR`.
- `priority` — `LOW` | `MEDIUM` | `HIGH` | `URGENT`
- `apartmentId` — UUID
- `assignedToUserId` — UUID
- `assignedToContractorId` — UUID
- `from` — ISO date (filter by `createdAt`)
- `to` — ISO date
- `slaBreached` — bool — **spec-only placeholder, NOT implemented**; use `overdue` below (the implemented equivalent).
- `overdue` — bool (P2.6). `true` = breached-open tickets only: `sla_deadline IS NOT NULL AND sla_deadline < NOW() AND status NOT IN (DONE, CANCELLED)` — the canonical SLA-breach predicate, mirrored from the SLA scheduler + report aggregates, so the count matches the Reports `slaBreached` / dashboard `overdueRequests` for the same dataset. `false` = the complement (not overdue: NULL-deadline OR future OR closed). Omitted = no SLA filtering (unchanged behavior). Applied **on top of** role-scope — exposes no ticket outside the caller's authorized scope; a NULL deadline never matches `true`.
- `mine` — bool (P2.8), **server-derived**. `true` = only tickets assigned to the calling user: `assigned_to_user_id = <caller principal id>`. The caller id is taken from the authenticated principal (the same id role-scoping uses) — **no client-supplied user id is accepted** (deliberately avoids the IDOR an `assigneeId=<id>` param would create). `false`/omitted = **no assignee filtering (no-op)** — only `mine=true` is active (`mine=false` is NOT a complement). Applied **on top of** role-scope — exposes no ticket outside the caller's authorized scope; a NULL assignee never matches `true` (unassigned tickets excluded). For TECHNICIAN, `(assigned-to-me OR status=NEW) AND mine=true` collapses to assigned-to-me (strict subset). Composable with `overdue` (the two AND).
- `search` — substring search on `title`
- `visibility` — **RESIDENT only** (N3 P5): `mine` | `community`. Omitted/`null` = `mine` = the pre-N3 scoping (own apartment) — existing clients keep prior behavior unchanged. `community` = public tickets only (`is_public = true`, any apartment), rows REDACTED for non-household viewers (see GET /api/tickets/{id} redaction rule). Any other value yields `400 VALIDATION_ERROR`. Ignored for staff roles.

Default sort: `createdAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
      "submittedBy": { "id": "uuid", "fullName": "string" },
      "category": "MAINTENANCE_REPAIR",
      "title": "Air conditioner not cooling",
      "status": "ASSIGNED",
      "priority": "HIGH",
      "assignedToUser": { "id": "uuid", "fullName": "Tran Van B" },
      "assignedToContractor": null,
      "slaDeadline": "2026-05-30T10:00:00Z",
      "slaBreached": false,
      "rating": null,
      "createdAt": "2026-05-29T10:00:00Z",
      "updatedAt": "2026-05-29T11:00:00Z"
    }
  ],
  "page": 0, "size": 20, "total": 45, "totalPages": 3
}
```

---

### POST /api/tickets

**Auth:** ADMIN, RESIDENT
**Description:** Submit a new ticket.

Request:
```json
{
  "apartmentId": "uuid",
  "category": "MAINTENANCE_REPAIR|COMPLAINT|ADMINISTRATIVE|SUGGESTION_FEEDBACK|OTHER",
  "title": "string (max 255)",
  "description": "string|null",
  "priority": "LOW|MEDIUM|HIGH|URGENT",
  "isPublic": "bool — optional, default false. Creator-chosen community visibility; IMMUTABLE after create (no edit path; rogue isPublic fields in other payloads are ignored)."
}
```

Response `201 Created` — ticket summary object (same shape as list item).

**Side effects:**
- `sla_deadline` computed from category default SLA and stored.
- Status history entry `null → NEW` created.
- All active ADMIN users receive an in-app `TICKET_CREATED` notification, excluding the acting user (see §12 event catalog).
- Creator joins the ticket notification thread (subscription row `CREATOR`).

---

### GET /api/tickets/{id}

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own apartment), BOARD_MEMBER

Response `200 OK` — full ticket detail:
```json
{
  "id": "uuid",
  "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
  "submittedBy": { "id": "uuid", "fullName": "string", "phone": "string" },
  "category": "MAINTENANCE_REPAIR",
  "title": "string",
  "description": "string|null",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assignedToUser": { "id": "uuid", "fullName": "string", "phone": "string" },
  "assignedToContractor": null,
  "scheduledDate": "2026-05-30",
  "completedDate": null,
  "rating": null,
  "ratingComment": null,
  "slaDeadline": "2026-05-30T10:00:00Z",
  "slaBreached": false,
  "resolutionNotes": null,
  "isPublic": false,
  "isFollowing": null,
  "redacted": false,
  "photos": [
    {
      "id": "uuid",
      "phase": "BEFORE",
      "presignedUrl": "https://minio.../...",
      "fileName": "photo1.jpg",
      "mimeType": "image/jpeg",
      "fileSizeBytes": 204800,
      "uploadedAt": "ISO8601"
    }
  ],
  "statusHistory": [
    {
      "id": "uuid",
      "oldStatus": null,
      "newStatus": "NEW",
      "changedBy": { "id": "uuid", "fullName": "string" },
      "notes": null,
      "changedAt": "ISO8601"
    },
    {
      "id": "uuid",
      "oldStatus": "NEW",
      "newStatus": "ASSIGNED",
      "changedBy": { "id": "uuid", "fullName": "Admin" },
      "notes": "Assigned to technician",
      "changedAt": "ISO8601"
    }
  ],
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

**Viewer flags (N3 P7):**
- `isPublic` — the creator-chosen community visibility flag.
- `isFollowing` — `true` iff the calling RESIDENT holds a FOLLOWER subscription row on this ticket. CREATOR/ASSIGNEE thread rows do NOT count. `null` for staff roles and on mutation responses.
- `redacted` — `true` iff this response was produced by the redacted public-view mapping below; `false` for household members and staff.

**Redaction rule (N3 P5/G8)** — a RESIDENT outside the ticket's household can read a PUBLIC ticket but receives the REDACTED shape (private tickets stay `403 FORBIDDEN`):
- **Visible:** title, description, category, status, priority, block name (`apartment.block.name` only), createdAt, status-history timestamps + statuses, resolutionNotes, slaBreached.
- **Hidden:** submitter identity — `submittedBy.fullName` is the literal placeholder «Cư dân» with no id/phone; apartment `id`/`unitNumber`; assignee identities; `photos` always `[]` (presign also denied — photos stay household/staff-only until F-05 lands); status-history `changedBy` + `notes`; `rating` + `ratingComment`; `scheduledDate`/`completedDate`/`slaDeadline`.
- Community LIST rows (`visibility=community`) are redacted with the same rule.

---

### PUT /api/tickets/{id}/assign

**Auth:** ADMIN
**Description:** Assign a ticket to an internal staff member or (for MAINTENANCE_REPAIR only) a contractor. Mutually exclusive — only one assignee at a time.

Request:
```json
{
  "assignedToUserId": "uuid|null",
  "assignedToContractorId": "uuid|null",
  "scheduledDate": "2026-05-30",
  "notes": "string|null"
}
```

Response `200 OK` — updated ticket summary.

Errors:
- `400 VALIDATION_ERROR` — both `assignedToUserId` and `assignedToContractorId` provided simultaneously.
- `400 VALIDATION_ERROR` — `assignedToContractorId` provided but `category != MAINTENANCE_REPAIR`.
- `404 NOT_FOUND` — assignee not found.

**Side effects:**
- Status transitions to `ASSIGNED`.
- Assignee receives an in-app + push notification.

---

### PUT /api/tickets/{id}/status

**Auth:** ADMIN, TECHNICIAN (own assigned tickets only)
**Description:** Update ticket status. Residents cannot call this endpoint.

Request:
```json
{
  "status": "IN_PROGRESS|DONE|CANCELLED|ASSIGNED",
  "notes": "string|null",
  "resolutionNotes": "string|null"
}
```

Response `200 OK` — updated ticket summary.

Errors: `409 CONFLICT` (invalid status transition — see Appendix A)

**Side effects:**
- Status history entry created.
- Submitting resident notified of status change.
- On `DONE`: contractor rating prompt notification sent to resident.

---

### POST /api/tickets/{id}/photos

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own apartment — BEFORE phase only)
**Description:** Upload one or more photos to a ticket.

Request: `multipart/form-data`
- `files` — one or more image files (`image/jpeg` or `image/png`, max 10 MB each, max 5 per request)
- `phase` — `BEFORE` | `PROGRESS` | `AFTER`

Response `201 Created`:
```json
{
  "uploaded": [
    {
      "id": "uuid",
      "phase": "BEFORE",
      "fileName": "photo.jpg",
      "fileSizeBytes": 102400,
      "uploadedAt": "ISO8601"
    }
  ]
}
```

Errors: `400 VALIDATION_ERROR` (unsupported MIME type), `413` (file exceeds size limit)

---

### DELETE /api/tickets/{id}/photos/{photoId}

**Auth:** ADMIN, TECHNICIAN (assigned)
**Description:** Remove a single photo from a ticket. `{photoId}` is the photo UUID.

Response `204 No Content`

Errors: `404 NOT_FOUND` (ticket or photo not found)

---

### POST /api/tickets/{id}/rate

**Auth:** RESIDENT (own apartment, only when `status = DONE`, one rating per ticket)
**Description:** Submit a satisfaction rating after the ticket is resolved.

Request:
```json
{
  "rating": 4,
  "comment": "string|null (max 500 chars)"
}
```

Response `200 OK`

Errors: `409 CONFLICT` (already rated, or ticket not in DONE status)

**Side effects:**
- If ticket was assigned to a contractor, contractor's average `rating` is recalculated.

---

### POST /api/tickets/{id}/follow

**Auth:** RESIDENT
**Description:** Opt-in follow of a ticket — inserts a FOLLOWER subscription row joining the ticket's notification thread (the caller then receives status-change events, see §12). Idempotent: following an already-followed ticket is a no-op `204`.

Response `204 No Content`
Errors: `404 NOT_FOUND` — ticket does not exist **or** is a private ticket outside the caller's household (deliberately indistinguishable: existence of invisible tickets must not leak).

---

### DELETE /api/tickets/{id}/follow

**Auth:** RESIDENT
**Description:** Unfollow — deletes the caller's subscription row. Idempotent: unfollowing a not-followed ticket is a no-op `204`. Same `404` rule for invisible private tickets.

Response `204 No Content`

---

### GET /api/tickets/sla-report

**Auth:** ADMIN, BOARD_MEMBER
**Description:** SLA performance report grouped by category.

Query params: `from` (ISO date), `to` (ISO date), `category`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "summary": {
    "total": 150,
    "completed": 120,
    "slaBreached": 12,
    "slaBreachRate": 0.10,
    "avgResolutionHours": 18.5,
    "avgRating": 4.1
  },
  "byCategory": [
    {
      "category": "MAINTENANCE_REPAIR",
      "total": 60,
      "completed": 55,
      "slaBreached": 4,
      "slaBreachRate": 0.07,
      "avgResolutionHours": 20.3,
      "avgRating": 4.2
    },
    {
      "category": "COMPLAINT",
      "total": 30,
      "completed": 28,
      "slaBreached": 5,
      "slaBreachRate": 0.17,
      "avgResolutionHours": 36.0,
      "avgRating": 3.8
    },
    {
      "category": "ADMINISTRATIVE",
      "total": 25,
      "completed": 22,
      "slaBreached": 2,
      "slaBreachRate": 0.08,
      "avgResolutionHours": 48.0,
      "avgRating": 4.5
    },
    {
      "category": "SUGGESTION_FEEDBACK",
      "total": 20,
      "completed": 10,
      "slaBreached": 1,
      "slaBreachRate": 0.05,
      "avgResolutionHours": 96.0,
      "avgRating": null
    },
    {
      "category": "OTHER",
      "total": 15,
      "completed": 5,
      "slaBreached": 0,
      "slaBreachRate": 0.0,
      "avgResolutionHours": 24.0,
      "avgRating": 4.0
    }
  ]
}
```

---

## 7. Amenities

### GET /api/amenities

**Auth:** Any authenticated role
**Description:** List all active amenities.

Response `200 OK`:
```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Swimming Pool",
      "description": "Outdoor rooftop pool",
      "location": "Rooftop",
      "capacity": 30,
      "openingTime": "06:00",
      "closingTime": "21:00",
      "maxDailyBookingsPerResident": 1,
      "requiresApproval": false,
      "isActive": true
    }
  ]
}
```

---

### POST /api/amenities

**Auth:** ADMIN

Request:
```json
{
  "name": "string",
  "description": "string|null",
  "location": "string|null",
  "capacity": 20,
  "openingTime": "06:00",
  "closingTime": "22:00",
  "maxDailyBookingsPerResident": 1,
  "requiresApproval": false
}
```

Response `201 Created` — amenity object.
Errors: `409 AMENITY_NAME_EXISTS` (name already taken), `400 VALIDATION_ERROR`

---

### GET /api/amenities/{id}

**Auth:** Any authenticated role
**Description:** Return a single amenity by id.

Response `200 OK` — `AmenityResponse` (same shape as the amenity list item).

Errors: `404 NOT_FOUND`

---

### PUT /api/amenities/{id}

**Auth:** ADMIN
Request: same as POST.
Response `200 OK` — updated amenity object.
Errors: `409 AMENITY_NAME_EXISTS` (name already taken by another amenity), `404 NOT_FOUND`, `400 VALIDATION_ERROR`

---

### DELETE /api/amenities/{id}

**Auth:** ADMIN
**Description:** Deactivate amenity (`is_active = false`). Cancels any pending future bookings with a system notification to affected residents.

> ⚠️ **Divergence (N3 audit):** the resident cancellation notification is NOT implemented — bookings/amenities are TEMP_HIDDEN on the resident FE; wire the notification when bookings unhide (cut from N3 v1 per CTO ruling G1).

Response `204 No Content`

---

### GET /api/amenities/{id}/availability

**Auth:** Any authenticated role
**Description:** Return booked time slots and remaining capacity for a given date.

Query params: `date` (required, ISO date)

Response `200 OK`:
```json
{
  "amenityId": "uuid",
  "date": "2026-06-01",
  "openingTime": "06:00",
  "closingTime": "21:00",
  "bookedSlots": [
    { "startTime": "09:00", "endTime": "10:00" },
    { "startTime": "14:00", "endTime": "15:30" }
  ],
  "remainingCapacity": 3
}
```

---

### GET /api/amenity-bookings

**Auth:** ADMIN, TECHNICIAN, BOARD_MEMBER (all bookings), RESIDENT (own bookings only)
Query params: `amenityId`, `status`, `from`, `to`, `residentId` (ADMIN only)
Default sort: `bookingDate desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "amenity": { "id": "uuid", "name": "Swimming Pool" },
      "resident": { "id": "uuid", "user": { "id": "uuid", "fullName": "string" } },
      "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
      "bookingDate": "2026-06-01",
      "startTime": "09:00",
      "endTime": "10:00",
      "status": "APPROVED",
      "notes": "string|null",
      "approvedBy": { "id": "uuid", "fullName": "string" },
      "approvedAt": "ISO8601",
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### POST /api/amenity-bookings

**Auth:** RESIDENT
**Description:** Submit a booking request. If `amenity.requiresApproval = false`, status is automatically set to `APPROVED`.

> **[PLANNED — multi-residency attribution]** When the caller holds multiple concurrent active
> residencies, the booking is currently attributed to a deterministically-selected residency
> (primary-contact first, else latest move-in, tie-break by id) — a SAFE TEMPORARY rule landed in
> residency-lifecycle P1 so the endpoint never throws under multi-residency. For a single-residency
> caller the behavior is unchanged. The real attribution rule (which apartment a multi-residency
> booking is charged to, or whether the request should carry an explicit apartment context) is
> **pending CTO ruling**.

Request:
```json
{
  "amenityId": "uuid",
  "bookingDate": "2026-06-01",
  "startTime": "09:00",
  "endTime": "10:00",
  "notes": "string|null"
}
```

Response `201 Created` — booking object.

Errors:
- `409 CONFLICT` — time slot unavailable (conflict with existing APPROVED booking).
- `409 CONFLICT` — daily booking limit reached for this resident and amenity.

---

### GET /api/amenity-bookings/{id}

**Auth:** ADMIN, RESIDENT (own booking)

Response `200 OK` — full booking detail.

---

### PUT /api/amenity-bookings/{id}/approve

**Auth:** ADMIN
**Description:** Approve OR reject a pending booking via a unified endpoint. Use `status` to discriminate.

Request:
```json
{
  "status": "APPROVED | REJECTED",
  "rejectionReason": "string|null"
}
```

`status` is required (`@NotNull`). `rejectionReason` should be provided when `status = REJECTED`.

Response `200 OK`
Errors: `409 BOOKING_NOT_PENDING` (booking is not in PENDING status), `404 NOT_FOUND`

> **Note:** A separate `/reject` endpoint was specified in the original design but was not implemented.
> Rejection is performed via this same `/approve` endpoint with `status: "REJECTED"`.
> API-SPEC updated 2026-06-04 to match as-built backend.

---

### PUT /api/amenity-bookings/{id}/cancel

**Auth:** ADMIN, RESIDENT (own booking when status is PENDING or APPROVED and booking_date is in the future)

Request:
```json
{ "reason": "string|null" }
```

Response `200 OK`

---

## 8. Contractors and Contracts

### GET /api/contractors

**Auth:** ADMIN, TECHNICIAN, BOARD_MEMBER
Query params: `specialty`, `isActive`, `search` (company name substring)
Default sort: `companyName asc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "companyName": "ABC Cleaning Co.",
      "contactPerson": "Nguyen Van C",
      "phone": "0909123456",
      "email": "contact@abc.vn",
      "specialty": "CLEANING",
      "rating": 4.20,
      "isActive": true
    }
  ]
}
```

---

### POST /api/contractors

**Auth:** ADMIN

Request:
```json
{
  "companyName": "string",
  "contactPerson": "string|null",
  "phone": "string|null",
  "email": "string|null",
  "address": "string|null",
  "specialty": "CLEANING|SECURITY|ELEVATOR|FIRE_SAFETY|LANDSCAPING|PEST_CONTROL|ELECTRICAL|PLUMBING|OTHER",
  "taxCode": "string|null",
  "notes": "string|null"
}
```

Response `201 Created` — contractor object.

---

### GET /api/contractors/{id}

**Auth:** ADMIN, TECHNICIAN, BOARD_MEMBER
**Description:** Full contractor profile including active contract count and computed average rating.

Response `200 OK`:
```json
{
  "id": "uuid",
  "companyName": "string",
  "contactPerson": "string",
  "phone": "string",
  "email": "string",
  "address": "string",
  "specialty": "CLEANING",
  "taxCode": "string",
  "rating": 4.20,
  "notes": "string",
  "isActive": true,
  "activeContractsCount": 2,
  "totalTicketsAssigned": 15,
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/contractors/{id}

**Auth:** ADMIN
Request: same as POST.
Response `200 OK` — updated contractor object.

---

### DELETE /api/contractors/{id}

**Auth:** ADMIN
**Description:** Deactivate contractor (`is_active = false`). Cannot deactivate if there are active contracts.

Response `204 No Content`
Errors: `409 CONFLICT` (contractor has active contracts)

---

### GET /api/contractors/{id}/work-history

> 🚧 **[PLANNED — chưa implement]** No controller mapping. NOT a duplicate: this returns **tickets** assigned to a contractor, which no live endpoint serves — `GET /api/tickets` does not bind an `assignedToContractorId` filter (TicketController:106-128), and `GET /api/contractors/{id}/contracts` returns contracts, not tickets. Genuinely planned-but-unbuilt; kept as intent record. Resolution: `reports/c-p5-stale-resolution.md` S1.

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Tickets that were assigned to this contractor.

Query params: `from`, `to`, `status`
Default sort: `createdAt desc`

Response `200 OK` — paginated list of ticket summaries (same shape as ticket list item).

---

### GET /api/contractors/{id}/contracts

**Auth:** ADMIN, BOARD_MEMBER
**Description:** List the contracts belonging to a contractor. As-built contract LIST surface (nested under the contractor).

Query params: `page` (default 0), `size` (default 20)

Response `200 OK` — `PageResponse<ContractResponse>` (each item same shape as the `GET /api/contracts` list item below).

---

### POST /api/contractors/{id}/contracts

**Auth:** ADMIN
**Description:** Create a contract for a contractor. As-built contract CREATE surface (nested under the contractor; `{id}` is the contractor UUID, so no `contractorId` in the body).

Request:
```json
{
  "title": "string",
  "scope": "string|null",
  "contractValue": 120000000,
  "currency": "VND",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "notes": "string|null"
}
```

Response `201 Created` — `ContractResponse` (contract summary object).

---

### GET /api/contracts

> 🚧 **[PLANNED — chưa implement]** No controller mapping. NOT a duplicate: the live `GET /api/contractors/{id}/contracts` (above) lists contracts for **one** contractor with `page`/`size` only — it does not cover this system-wide, cross-contractor filtered list (`contractorId`/`status`/`expiringWithinDays`/`from`/`to`). Genuinely planned-but-unbuilt; kept as intent record. Resolution: `reports/c-p5-stale-resolution.md` S2.

**Auth:** ADMIN, BOARD_MEMBER
Query params: `contractorId`, `status`, `expiringWithinDays` (int), `from`, `to`
Default sort: `endDate asc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "contractor": { "id": "uuid", "companyName": "ABC Cleaning Co." },
      "title": "Cleaning Service Contract 2026",
      "contractValue": 120000000,
      "currency": "VND",
      "startDate": "2026-01-01",
      "endDate": "2026-12-31",
      "status": "ACTIVE",
      "daysToExpiry": 216
    }
  ]
}
```

---

### GET /api/contracts/{id}

**Auth:** ADMIN, BOARD_MEMBER, TECHNICIAN
**Description:** Full contract detail including payments and schedules.

Response `200 OK`:
```json
{
  "id": "uuid",
  "contractor": { "id": "uuid", "companyName": "string", "specialty": "CLEANING" },
  "title": "string",
  "scope": "string",
  "contractValue": 120000000,
  "currency": "VND",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "ACTIVE",
  "hasAttachment": true,
  "notes": "string",
  "createdBy": { "id": "uuid", "fullName": "string" },
  "totalPaid": 40000000,
  "schedulesCount": 3,
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/contracts/{id}

**Auth:** ADMIN
Request: same as POST minus `contractorId`.
Response `200 OK` — updated contract object.

---

### POST /api/contracts/{id}/attachment

> ⚠️ **NOT IMPLEMENTED + security gate (R-4):** this endpoint is spec'd but has no controller code. When built, it MUST validate contract-level access (staff-only) through the presign ownership check before touching MinIO — see the file-surface access matrix in §13.

**Auth:** ADMIN
**Description:** Upload or replace the PDF attachment for a contract.

Request: `multipart/form-data` — field `file` (PDF only, max 20 MB)

Response `200 OK`:
```json
{ "objectKey": "contracts/<id>/attachment/contract.pdf" }
```

---

### GET /api/contracts/{id}/attachment

> ⚠️ **NOT IMPLEMENTED + security gate (R-4):** this endpoint is spec'd but has no controller code. When built, it MUST validate contract-level access (staff-only) through the presign ownership check before touching MinIO — see the file-surface access matrix in §13.

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Get a short-lived presigned download URL for the contract attachment.

Response `200 OK`:
```json
{
  "presignedUrl": "https://minio.../...",
  "expiresAt": "2026-05-29T11:00:00Z"
}
```

Errors: `404 NOT_FOUND` (no attachment uploaded)

---

### POST /api/contracts/{id}/payments

**Auth:** ADMIN
**Description:** Record a payment against a contract (record-only, not a disbursement approval).

Request:
```json
{
  "amount": 10000000,
  "paymentDate": "2026-05-01",
  "description": "string|null",
  "referenceNumber": "string|null"
}
```

Response `201 Created`:
```json
{
  "id": "uuid",
  "contractId": "uuid",
  "amount": 10000000,
  "paymentDate": "2026-05-01",
  "description": "string",
  "referenceNumber": "string",
  "recordedBy": { "id": "uuid", "fullName": "string" },
  "createdAt": "ISO8601"
}
```

---

### GET /api/contracts/{id}/payments

**Auth:** ADMIN
Default sort: `paymentDate desc`

Response `200 OK` — paginated list of payment objects.

---

### GET /api/contracts/{id}/schedules

**Auth:** ADMIN, TECHNICIAN

Response `200 OK`:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Monthly HVAC Inspection",
      "description": "string",
      "frequency": "MONTHLY",
      "nextDueDate": "2026-06-01",
      "lastDoneDate": "2026-05-01",
      "isActive": true
    }
  ]
}
```

---

### POST /api/contracts/{id}/schedules

**Auth:** ADMIN

Request:
```json
{
  "title": "Monthly HVAC Inspection",
  "description": "string|null",
  "frequency": "DAILY|WEEKLY|MONTHLY|QUARTERLY|ANNUAL",
  "nextDueDate": "2026-06-01",
  "notes": "string|null"
}
```

Response `201 Created` — schedule object.

---

### PUT /api/maintenance-schedules/{id}

> 🚧 **[PLANNED — chưa implement]** No controller mapping. NOT a duplicate: maintenance schedules have create + list only (`POST`/`GET /api/contracts/{id}/schedules`, ContractorController:280-298); no update path exists anywhere. Genuinely planned-but-unbuilt; kept as intent record. Resolution: `reports/c-p5-stale-resolution.md` S4.

**Auth:** ADMIN

Request:
```json
{
  "title": "string",
  "description": "string|null",
  "frequency": "MONTHLY",
  "nextDueDate": "2026-07-01",
  "lastDoneDate": "2026-06-01",
  "isActive": true,
  "notes": "string|null"
}
```

Response `200 OK` — updated schedule object.

---

## 9. Parking

### GET /api/parking/slots

**Auth:** ADMIN, TECHNICIAN
Query params: `type`, `status`, `zone`
Default sort: `slotNumber asc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "slotNumber": "B1-001",
      "zone": "B1",
      "type": "CAR",
      "status": "OCCUPIED",
      "currentAssignment": {
        "id": "uuid",
        "vehicle": { "licensePlate": "51A-123.45", "brand": "Toyota", "type": "CAR" },
        "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
        "parkingCardNumber": "PC-0012",
        "startDate": "2026-01-01"
      }
    }
  ]
}
```

---

### POST /api/parking/slots

**Auth:** ADMIN

Request:
```json
{
  "slotNumber": "B1-001",
  "zone": "B1",
  "type": "CAR|MOTORBIKE|BICYCLE",
  "notes": "string|null"
}
```

Response `201 Created` — parking slot object.
Errors: `409 CONFLICT` (slot number already exists)

---

### PUT /api/parking/slots/{id}

**Auth:** ADMIN
Request: same as POST minus `slotNumber`.
Response `200 OK`

---

### GET /api/parking/slots/{id}

**Auth:** ADMIN, TECHNICIAN
**Description:** Return a single parking slot, including its current assignment if any.

Response `200 OK` — `ParkingSlotResponse` (same shape as the slot list item).

Errors: `404 NOT_FOUND`

---

### DELETE /api/parking/slots/{id}

**Auth:** ADMIN
**Description:** Delete a parking slot.

Response `204 No Content`

Errors: `409 CONFLICT` (slot has an active assignment)

---

### POST /api/parking/slots/{id}/assign

**Auth:** ADMIN
**Description:** Assign a parking slot to a vehicle. `{id}` is the parking slot UUID (also required in body).

Request:
```json
{
  "parkingSlotId": "uuid",
  "vehicleId": "uuid",
  "apartmentId": "uuid",
  "startDate": "2026-01-01",
  "parkingCardNumber": "string|null",
  "notes": "string|null"
}
```

`endDate` is not supported on create — assignments are open-ended; use the unassign endpoint to close them.

Response `201 Created` — assignment object.
Errors: `409 CONFLICT` (slot already has an active assignment)

> **Note:** Original spec used `POST /parking/assignments`. As-built backend uses `POST /parking/slots/{id}/assign`.
> API-SPEC updated 2026-06-04 to match as-built backend.

---

### GET /api/parking/assignments

**Auth:** ADMIN
Query params (as-built): `apartmentId`, `slotId`, `active` (bool)
Default sort: `startDate desc`

> **Reconciliation v2.2:** the controller binds `apartmentId`, `slotId`, `active` (+ `page`/`size`). The prior `vehicleId` param was spec-only (not implemented) and `isActive` is named `active` in code.

Response `200 OK` — paginated list of assignment objects.

---

### GET /api/parking/assignments/{id}

**Auth:** ADMIN
**Description:** Return a single parking assignment by id.

Response `200 OK` — `ParkingAssignmentResponse` (same shape as the assignment list item).

Errors: `404 NOT_FOUND`

---

### POST /api/parking/slots/{id}/unassign

**Auth:** ADMIN
**Description:** End the active assignment for a slot. `{id}` is the **slot** UUID (not the assignment UUID).

Request:
```json
{ "endDate": "2026-06-01" }
```

`endDate` is optional; defaults to today if omitted.

Response `200 OK`

> **Note:** Original spec used `PUT /parking/assignments/{id}/end`. As-built backend uses `POST /parking/slots/{id}/unassign`.
> API-SPEC updated 2026-06-04 to match as-built backend.

---

### GET /api/parking/guests

> **Reconciliation v2.2:** as-built path is `/api/parking/guests` (was documented as `/guest-vehicles`). Role and params corrected to match controller.

**Auth:** ADMIN, TECHNICIAN
Query params (as-built): `apartmentId`, `active` (bool)
Default sort: `entryTime desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "licensePlate": "51A-999.99",
      "ownerName": "string",
      "hostApartment": { "id": "uuid", "unitNumber": "A301" },
      "entryTime": "ISO8601",
      "exitTime": "ISO8601|null",
      "purpose": "string|null",
      "recordedBy": { "id": "uuid", "fullName": "string" }
    }
  ]
}
```

---

### POST /api/parking/guests

**Auth:** ADMIN, TECHNICIAN
**Description:** Log a guest vehicle entry. (As-built path is `/api/parking/guests` — was `/guest-vehicles`.)

Request:
```json
{
  "licensePlate": "51A-999.99",
  "ownerName": "string|null",
  "hostApartmentId": "uuid",
  "purpose": "string|null",
  "notes": "string|null"
}
```

Response `201 Created` — guest vehicle object.

---

### PUT /api/parking/guests/{id}/checkout

**Auth:** ADMIN, TECHNICIAN
**Description:** Record guest vehicle exit. (As-built path is `/api/parking/guests/{id}/checkout` — was `/guest-vehicles/{id}/exit`.)

Request:
```json
{ "exitTime": "ISO8601|null (defaults to server current time)" }
```

Response `200 OK`

---

## 10. Announcements

### GET /api/announcements

**Auth:** Any authenticated role
**Description:** Residents see only announcements targeted to their block/floor/all. Admins see all including drafts.

Query params: `type`, `from`, `to`, `isPublished` (bool, default: `true`), `blockId`, `floor`, `search`
Default sort: `publishedAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Scheduled Water Shutdown",
      "type": "MAINTENANCE",
      "targetScope": "BLOCK",
      "targetBlock": { "id": "uuid", "name": "Block A" },
      "targetFloor": null,
      "publishedAt": "ISO8601",
      "createdBy": { "id": "uuid", "fullName": "string" },
      "isRead": false,
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### POST /api/announcements

**Auth:** ADMIN

Request:
```json
{
  "title": "string",
  "content": "string",
  "type": "GENERAL|URGENT|MAINTENANCE|AMENITY|EVENT",
  "targetScope": "ALL|BLOCK|FLOOR",
  "targetBlockId": "uuid|null",
  "targetFloor": "number|null",
  "sendPush": true,
  "sendEmail": false,
  "sendSms": false,
  "publishNow": true
}
```

Response `201 Created` — announcement summary object.

**Content format:** `content` is **Markdown** stored raw in the existing `TEXT` column (no schema/content-type
change). It is rendered XSS-safe on the frontend (React-element render — no raw HTML, scheme-filtered links).
Write-time secondary guard (defense-in-depth): max length **20000 chars** → `400 ANNOUNCEMENT_CONTENT_TOO_LONG`;
raw HTML tags rejected → `400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED` (the stored format is Markdown, not HTML).

**Side effects when `publishNow: true`:**
- `published_at` set to NOW().
- FCM push / email / SMS dispatched asynchronously to target audience.
- In-app notifications created for all targeted users.

---

### GET /api/announcements/{id}

**Auth:** Any authenticated role (same scoping rules as list)

Response `200 OK` — full announcement detail including full `content` text.

---

### PUT /api/announcements/{id}

**Auth:** ADMIN
**Description:** Update a draft announcement. Cannot modify already-published announcements.

Request: same as POST minus `publishNow`. `content` follows the same Markdown contract and write-time
guards as POST (`400 ANNOUNCEMENT_CONTENT_TOO_LONG` / `400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`).
Response `200 OK`
Errors: `409 CONFLICT` (announcement is already published)

---

### POST /api/announcements/{id}/publish

**Auth:** ADMIN
**Description:** Publish a draft announcement and trigger notification delivery.

Request: (no body)
Response `200 OK`
Errors: `409 CONFLICT` (announcement already published)

---

### POST /api/announcements/{id}/read

**Auth:** Any authenticated role
**Description:** Mark announcement as read for the calling user.

Request: (no body)
Response `204 No Content`

---

### GET /api/announcements/{id}/read-stats

**Auth:** ADMIN
**Description:** Read receipt statistics for an announcement.

Response `200 OK`:
```json
{
  "announcementId": "uuid",
  "targetAudienceCount": 200,
  "readCount": 145,
  "readRate": 0.725,
  "unreadUsers": [
    { "id": "uuid", "fullName": "string", "apartment": "A301", "block": "Block A" }
  ]
}
```

---

### POST /api/announcements/{id}/media

**Auth:** ADMIN
**Description:** Upload ONE image to a **draft** announcement (C2.2). Published announcements are immutable
(reject). `multipart/form-data`.

Request parts:
- `file` (required) — the image. Real content type is validated by **Tika on the bytes** (magic number),
  NOT the filename extension or client `Content-Type`. Allowed: **jpg / png / webp** only.
- `kind` (required) — `cover` | `inline` (case-insensitive). At most ONE `cover` per announcement: a second
  `cover` upload **REPLACES** the existing one (old row removed + old object deleted after commit).

Server-enforced caps (per announcement, inside the upload transaction): **≤5 images** AND **≤50 MB total**.
The stored object key follows the C2.1 convention `announcements/{announcementId}/{uuid}` so the presign gate
(`GET /api/files/presign`, §13) can parse the announcement id. The servlet multipart limit is 10 MB/file.

Response `201 Created`:
```json
{
  "id": "uuid",
  "kind": "COVER",
  "contentType": "image/jpeg",
  "sizeBytes": 184320,
  "originalFilename": "cover.jpg",
  "objectKey": "announcements/{announcementId}/{uuid}.jpg",
  "createdAt": "2026-06-23T10:00:00Z"
}
```
> No presigned URL is returned here — clients fetch a short-lived URL on read via `GET /api/files/presign`
> with the `objectKey` (C2.1 scope gate).

Errors:
- `400 ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED` — detected type is not jpg/png/webp (VN: *"Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP."*)
- `400 ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED` — would exceed 5 images (VN: *"Tối đa 5 ảnh mỗi thông báo."*) or 50 MB total (VN: *"Tổng dung lượng ảnh của thông báo vượt quá 50MB."*)
- `400 VALIDATION_ERROR` — missing/invalid `kind`
- `409 ANNOUNCEMENT_NOT_DRAFT` — announcement is published (VN: *"Không thể chỉnh sửa ảnh của thông báo đã xuất bản."*)
- `403 FORBIDDEN` — caller is not ADMIN
- `404 NOT_FOUND` — announcement does not exist

---

### GET /api/announcements/{id}/media

**Auth:** ADMIN, BOARD_MEMBER
**Description:** List an announcement's media metadata (authoring view), oldest first. Resident read of media
happens via the detail/render path (C2.3) gated by the C2.1 presign check — not this endpoint.

Response `200 OK`: array of the media object shown above (no presigned URLs).

---

### DELETE /api/announcements/{id}/media/{mediaId}

**Auth:** ADMIN
**Description:** Delete one media row from a **draft** announcement. The DB row is removed in-transaction; the
MinIO object is deleted **after commit** (best-effort — an orphaned object never rolls back the delete). The
`mediaId` must belong to `{id}` (dual-key — no cross-announcement delete).

Response `204 No Content`
Errors: `409 ANNOUNCEMENT_NOT_DRAFT` (published), `404 NOT_FOUND` (no such media in this announcement), `403 FORBIDDEN` (not ADMIN)

> **Draft delete cascade:** `DELETE /api/announcements/{id}` on a draft also removes all its media rows
> (FK `ON DELETE CASCADE`) and schedules every object for after-commit cleanup.

---

## 11. Reports and Dashboard

All report endpoints require `ADMIN` or `BOARD_MEMBER` role.

---

### GET /api/reports/dashboard

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Summary KPIs for the dashboard landing page.

Response `200 OK`:
```json
{
  "apartments": {
    "total": 1000,
    "occupied": 875,
    "available": 100,
    "maintenance": 25,
    "occupancyRate": 0.875
  },
  "tickets": {
    "openRequests": 23,
    "inProgressRequests": 15,
    "overdueRequests": 3,
    "avgResolutionHoursLast30Days": 20.5,
    "byCategory": {
      "MAINTENANCE_REPAIR": 10,
      "COMPLAINT": 5,
      "ADMINISTRATIVE": 3,
      "SUGGESTION_FEEDBACK": 2,
      "OTHER": 3
    }
  },
  "amenities": {
    "bookingsThisMonth": 145,
    "pendingApproval": 5
  },
  "contracts": {
    "active": 8,
    "expiringIn30Days": 2,
    "expiringIn90Days": 4
  }
}
```

---

### GET /api/reports/tickets

**Auth:** ADMIN, BOARD_MEMBER
Query params: `from`, `to`, `groupBy` (`month` | `category` | `status` | `assignee`), `category`, `apartmentId`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "summary": {
    "total": 150,
    "completed": 120,
    "cancelled": 5,
    "inProgress": 20,
    "new": 5,
    "slaBreachRate": 0.08,
    "avgRating": 4.1
  },
  "breakdown": [
    {
      "label": "2026-01",
      "total": 28,
      "completed": 25,
      "slaBreached": 1,
      "avgRating": 4.3
    }
  ]
}
```

---

### GET /api/reports/amenity-usage

**Auth:** ADMIN, BOARD_MEMBER
Query params: `from`, `to`, `amenityId`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "byAmenity": [
    {
      "amenity": { "id": "uuid", "name": "Swimming Pool" },
      "totalBookings": 85,
      "approvedBookings": 80,
      "rejectedBookings": 3,
      "cancelledBookings": 2,
      "peakDay": "2026-05-15",
      "utilizationRate": 0.62
    }
  ]
}
```

---

### GET /api/reports/contracts-expiring

**Auth:** ADMIN, BOARD_MEMBER
Query params: `withinDays` (default: `90`)

Response `200 OK`:
```json
{
  "asOf": "2026-05-29",
  "contracts": [
    {
      "id": "uuid",
      "title": "Cleaning Service Contract 2026",
      "contractor": { "id": "uuid", "companyName": "ABC Cleaning Co." },
      "endDate": "2026-07-31",
      "daysToExpiry": 63,
      "contractValue": 120000000,
      "currency": "VND",
      "status": "ACTIVE"
    }
  ]
}
```

---

### GET /api/reports/residents

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Occupancy and resident demographics.

Query params: `blockId`

Response `200 OK`:
```json
{
  "totalApartments": 1000,
  "occupiedApartments": 875,
  "occupancyRate": 0.875,
  "totalActiveResidents": 2100,
  "owners": 875,
  "tenants": 1225,
  "averageResidentsPerApartment": 2.4
}
```

---

## 12. Notifications

### GET /api/notifications

**Auth:** Any authenticated role
**Description:** List the calling user's notifications.

Query params: `isRead` (bool), `type`
Default sort: `createdAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Your ticket has been assigned",
      "body": "Ticket 'Air conditioner not cooling' has been assigned to Tran Van B.",
      "type": "TICKET_ASSIGNED",
      "referenceId": "uuid",
      "referenceType": "Ticket",
      "isRead": false,
      "createdAt": "ISO8601"
    }
  ],
  "page": 0, "size": 20, "total": 10, "totalPages": 1
}
```

Note: the unread count is NOT part of this page response — clients fetch it from `GET /api/notifications/unread-count`.

---

### POST /api/notifications/{id}/read

**Auth:** Any authenticated role (own notifications only)
**Description:** Mark a single notification as read.

Response `204 No Content`
Errors: `403 FORBIDDEN` (notification belongs to another user)

---

### POST /api/notifications/read-all

**Auth:** Any authenticated role
**Description:** Mark all of the calling user's notifications as read.

Response `204 No Content`

---

### GET /api/notifications/unread-count

**Auth:** Any authenticated role
**Description:** Return the count of the calling user's unread notifications (drives the FE bell badge). Identity is derived from the principal.

Response `200 OK`:
```json
{ "unreadCount": 5 }
```

---

### Notification event catalog (implemented — N3)

Thread = all `notification_subscriptions` rows for the ticket (CREATOR + ASSIGNEEs + FOLLOWERs), always excluding the acting user. All bodies are Vietnamese; ticket status labels use the locked FE terms (DONE = «Hoàn tất»).

| Event | Trigger | Type | Recipients |
|---|---|---|---|
| C1 ticket created | `POST /tickets` | `TICKET_CREATED` | active ADMINs (minus actor) |
| C2 ticket assigned (assignee) | `PUT /tickets/{id}/assign` | `TICKET_ASSIGNED` | assigned staff user |
| C3 ticket accepted (thread) | assign's NEW→ASSIGNED auto-transition | `TICKET_STATUS_CHANGED` | thread snapshot before assignee joins |
| C4 status changed | `PUT /tickets/{id}/status` | `TICKET_STATUS_CHANGED` | thread minus actor |
| C5 rating prompt | status → DONE | `TICKET_RATING_REQUESTED` | submitter only |
| C6 ticket rated | `POST /tickets/{id}/rate` | `TICKET_RATED` | assigned staff user |
| C7 SLA approaching | scheduler, every 15 min; deadline < now+2h, once per ticket | `TICKET_SLA_WARNING` | assignee (if any) + active ADMINs, deduped |
| C8 SLA overdue | same scheduler; deadline < now, once per ticket (a ticket first seen already overdue gets ONLY C8, never C7) | `TICKET_SLA_BREACHED` | assignee (if any) + active ADMINs, deduped |
| C9 household member added | `POST /residents` | `HOUSEHOLD_MEMBER_ADDED` | active residents of the apartment, minus the new user and the actor |
| announcement published | `POST /announcements/{id}/publish` | `ANNOUNCEMENT_PUBLISHED` | residents in the announcement scope |
| contract expiring | daily scheduler, once per contract (sent-marker) | `CONTRACT_EXPIRING` | contract's `createdBy` staff user |
| maintenance overdue | daily scheduler | `SCHEDULE_DUE` | linked contract's `createdBy` staff user |

`referenceType` values written by the BE: `Ticket`, `Announcement`, `Resident`, `Contract`, `MaintenanceSchedule` — these drive FE bell deep-links; unmapped types are mark-read-only on click.

---

## 13. Files

### GET /api/files/presign

**Auth:** Any authenticated role
**Description:** Get a short-lived presigned GET URL for a MinIO object (expiry: **10 minutes** — hardening H1). The server validates that the requesting user has permission to access the referenced object's parent entity before issuing the URL. Unknown keys yield `404`.

**File-surface access matrix (NORMATIVE — hardening H1/P-C, CTO-ratified):**

| Surface | Who may obtain a presigned URL |
|---|---|
| Ticket photos (`tickets/…` keys) | Active residents of the ticket's apartment; assigned TECHNICIAN; any TECHNICIAN while the ticket is in `NEW` status (triage rule, E5); ADMIN; BOARD_MEMBER. **Public-ticket visibility grants NO photo access — PERMANENT rule (E4): photos can show home interiors; any future "public photos" needs a per-photo creator-consent feature, not a presign widening.** |
| Contract attachments (endpoints below — NOT yet implemented) | Staff only (ADMIN/BOARD_MEMBER) when built; MUST route through an `assertPresignAccess`-style ownership check before issuing URLs (R-4). |
| Announcement images (`announcements/{announcementId}/{file}` keys) | **Scope-mirrored (C2.1)** — exactly whoever may READ the owning announcement: ADMIN/BOARD_MEMBER unrestricted (drafts included, for authoring preview); RESIDENT iff the announcement is PUBLISHED and its ALL/BLOCK/FLOOR scope matches one of the caller's ACTIVE residencies (the same predicate as the resident feed — `AnnouncementRepository.existsReadableByResident`); every other role denied. A DRAFT's media is ADMIN/BOARD-only. Malformed key or nonexistent announcement → `403` (never a 500). The pre-C2.1 any-authenticated stub is REMOVED. Key convention: the announcement id is the first path segment after the prefix so the gate recovers it from the key alone. Upload endpoint lands with C2.2. |

Query params: `objectKey` (required)

Response `200 OK`:
```json
{
  "presignedUrl": "https://minio.internal/bucket/tickets/.../photo.jpg?X-Amz-...",
  "expiresAt": "2026-05-29T11:00:00Z"
}
```

Errors: `403 FORBIDDEN` (user does not have access to the object's parent entity), `404 NOT_FOUND` (object does not exist in MinIO)

---

## Appendix A: Status Transition Rules

### Ticket

```
NEW        → ASSIGNED     (by ADMIN when assignee is set)
NEW        → CANCELLED    (by ADMIN or RESIDENT who submitted)
ASSIGNED   → IN_PROGRESS  (by assigned TECHNICIAN or ADMIN)
ASSIGNED   → ASSIGNED     (by ADMIN — reassign to different assignee)
ASSIGNED   → CANCELLED    (by ADMIN)
IN_PROGRESS → DONE        (by assigned TECHNICIAN or ADMIN)
IN_PROGRESS → ASSIGNED    (by ADMIN — reassignment)
IN_PROGRESS → CANCELLED   (by ADMIN only)
DONE        → (terminal — resident can still add rating)
CANCELLED   → (terminal)
```

**Note for SUGGESTION_FEEDBACK:** These tickets may go directly from `NEW` to `DONE` by an ADMIN without an ASSIGNED/IN_PROGRESS intermediate step, since there is no external assignee — the admin simply reviews and closes.

### Amenity Booking

```
PENDING   → APPROVED    (by ADMIN, or auto-approved if requiresApproval = false)
PENDING   → REJECTED    (by ADMIN)
PENDING   → CANCELLED   (by RESIDENT or ADMIN)
APPROVED  → CANCELLED   (by RESIDENT before booking_date, or ADMIN)
APPROVED  → COMPLETED   (by BookingCompletionScheduler at end of booking window)
REJECTED  → (terminal)
CANCELLED → (terminal)
COMPLETED → (terminal)
```

### Contract

```
PENDING    → ACTIVE      (by ADMIN after contract is signed and activated)
ACTIVE     → EXPIRED     (by ContractExpiryScheduler when end_date passes)
ACTIVE     → TERMINATED  (by ADMIN)
EXPIRED    → (terminal)
TERMINATED → (terminal)
```

---

## Appendix B: Ticket Category Routing Reference

| Category | Assign to staff | Assign to contractor | SLA default | Priority default |
|----------|----------------|---------------------|-------------|-----------------|
| `MAINTENANCE_REPAIR` | Yes | Yes | 24 hours | MEDIUM |
| `COMPLAINT` | Yes | No | 48 hours | MEDIUM |
| `ADMINISTRATIVE` | Yes (ADMIN/TECHNICIAN) | No | 72 hours | LOW |
| `SUGGESTION_FEEDBACK` | Admin review queue only | No | 168 hours | LOW |
| `OTHER` | Yes | No | 48 hours | LOW |

The CHECK constraint `chk_tickets_contractor_category` in the database enforces that `assigned_to_contractor_id` can only be non-null when `category = 'MAINTENANCE_REPAIR'`. The service layer also validates this before attempting to persist, returning `400 VALIDATION_ERROR` with message `CONTRACTOR_ASSIGNMENT_NOT_ALLOWED`.

---

## Appendix C: Rate Limiting

| Endpoint | Limit | Key |
|----------|-------|-----|
| `POST /api/auth/login` | 10 req/min | Per IP address |
| `POST /api/auth/refresh` | 20 req/min | Per user ID |
| `POST /api/tickets/{id}/photos` | 10 req/min | Per user ID |
| All other authenticated endpoints | 120 req/min | Per user ID |

Rate limit exceeded responses return `429 RATE_LIMITED` with header `Retry-After: <seconds>`.
