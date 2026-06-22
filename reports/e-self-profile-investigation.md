# (e) Self-Service Profile — READ-ONLY Investigation

**Date:** 2026-06-18 · **Branch:** deploy/local · **HEAD:** 0a65af4
**Scope:** self-service profile page for ALL admin-portal roles (ADMIN / BOARD_MEMBER / TECHNICIAN): view own profile, change own phone/email, change own password (with current-password verification).
**Discipline:** read-only. No code changed. Ground truth cited `file:line`.

**Tree state:** No tracked-file modifications. Working tree carries only untracked `reports/*` + `scripts/GenHash.java` artifacts (pre-existing). Clean for code purposes.

---

## §A — What EXISTS today

| Feature | Endpoint | Gate | Status |
|---|---|---|---|
| Read own profile | `GET /api/auth/me` | authenticated (any role) | ✅ EXISTS |
| Change own password (with old-pw verify) | `PUT /api/auth/me/password` | authenticated (any role) | ✅ EXISTS |
| Update own FCM token | `PUT /api/auth/me/fcm-token` | authenticated (any role) | ✅ (not in scope, noted) |
| **Update own profile (phone/email/fullName)** | — | — | ❌ **MISSING** |
| Admin update *any* user | `PUT /api/users/{id}` | `hasRole('ADMIN')` | exists, admin-only — NOT self-serviceable |
| Admin force-reset *any* pw (no old-pw verify) | `PUT /api/users/{id}/reset-password` | `hasRole('ADMIN')` | exists, admin-only |

### §A.1 — me-read (`GET /api/auth/me`) — EXISTS
- Controller `AuthController.getMe`, `AuthController.java:172-176`. **No `@PreAuthorize`** → governed by `SecurityConfig.java:118` `anyRequest().authenticated()`. Only `/api/auth/login` + `/api/auth/refresh` are `permitAll` (`SecurityConfig.java:106-107`). ⇒ **every authenticated role can call it**, including TECHNICIAN.
- Service `AuthServiceImpl.getMe`, `AuthServiceImpl.java:248-252`: loads `userRepository.findById(principal.getId())` — **server-derived identity**, no client-supplied id. IDOR-safe by construction.
- Returns `UserDetailResponse` (`UserDetailResponse.java:29-40`): `id, email, fullName, phone, role, dateOfBirth, avatarUrl, isActive, lastLoginAt, createdAt`.

### §A.2 — self change-password (`PUT /api/auth/me/password`) — EXISTS, fully correct
- Controller `AuthController.changePassword`, `AuthController.java:185-192`. Authenticated-only (same `anyRequest().authenticated()` rule).
- DTO `ChangePasswordRequest` (`ChangePasswordRequest.java:15-22`): `{ currentPassword, newPassword }`, both `@NotBlank`.
- Service `AuthServiceImpl.changePassword`, `AuthServiceImpl.java:257-276`:
  - **Verifies current password** — `passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())` → throws `WRONG_CURRENT_PASSWORD` (`:263-265`). This is the security gate the admin force-reset deliberately lacks.
  - **Enforces complexity** — same regex as admin reset: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,}$` → throws `PASSWORD_POLICY_VIOLATION` (`:268-271`). (Note: self-change enforces in service-body for a specific error code; admin reset enforces via DTO `@Pattern`, `ResetPasswordRequest.java:18-21` — same rule, different placement.)
  - Server-derived identity (`principal.getId()`).
- **Already consumed by the resident app** (`frontend/apps/resident/src/api/hooks.ts:112` → `put('/auth/me/password')`), proving the endpoint is live and FE-ready.

⇒ **Feature 3 (self change password with current-password verification) needs ZERO backend work.**

---

## §B — What's MISSING + the minimal BE addition

### Only ONE gap: self profile UPDATE (phone / email / fullName)
- **Confirmed no self-update path exists.** The only profile-mutating endpoints are:
  - `PUT /api/users/{id}` — `hasRole('ADMIN')` (`UserController.java:136-143`). A BOARD_MEMBER / TECHNICIAN **cannot** call it (403), and it takes a client-supplied target `id` (admin tool, not self-scoped).
  - `PUT /api/auth/me/password`, `PUT /api/auth/me/fcm-token` — narrow, do not touch phone/email/fullName.
- Cross-checked the resident app: its ProfilePage uses only `/auth/me` (read) + `/auth/me/password` (`resident/src/api/hooks.ts:12,112`). **No self profile-update endpoint anywhere in the codebase.**

#### Minimal BE addition (mirrors established patterns)
Add a self-scoped update on the auth/me surface (consistent with the existing `/me/*` family):

- **Endpoint:** `PUT /api/auth/me/profile` (new), in `AuthController`. No `@PreAuthorize` → authenticated-only via the existing `anyRequest().authenticated()` rule ⇒ all roles, including TECHNICIAN.
- **Identity:** server-derived from `principal.getId()` (same as `getMe`/`changePassword`, `AuthServiceImpl.java:249,260`). **Never accept a target id from the client** — IDOR-safe like the `mine=true` pattern.
- **DTO:** new `UpdateOwnProfileRequest` carrying the CTO-chosen self-editable subset (recommend `fullName`, `phone`, `email`; **exclude `role`, `isActive`** — those are the privilege-escalation fields the admin-only `UpdateUserRequest` carries, `UpdateUserRequest.java:29-33`). Reuse the same validation annotations: `@NotBlank @Size(max=255)` fullName, `@Size(max=20)` phone, `@Email @Size(max=255)` email (matching `CreateUserRequest.java:26-31` / `UpdateUserRequest.java:22-27`).
- **Phone handling:** normalize via `PhoneUtils.normalize()` (as `createUser`/`login` do, `UserServiceImpl.java:99`, `AuthServiceImpl.java:116`) and **add an explicit `PHONE_ALREADY_EXISTS` pre-check** like `createUser` (`UserServiceImpl.java:103-106`) — but guarded to *exclude the caller's own row* (i.e. `existsByPhone(normalized) && !current.phone.equals(normalized)`). ⚠️ Note the existing admin `updateUser` does **NOT** do this check (`UserServiceImpl.java:154` blind `setPhone`) — it relies on the DB `uq_users_phone` constraint, which would surface as an ugly `DataIntegrityViolation`/500 rather than a clean `PHONE_ALREADY_EXISTS`. The self-endpoint should do it right.
- **Email handling:** same shape using `EMAIL_ALREADY_EXISTS` + `existsByEmail` (pattern at `UserServiceImpl.java:110-113`), self-row-excluded, treat blank→null.

---

## §C — Security flags

1. **Current-password verification is mandatory for password change** — already satisfied (`AuthServiceImpl.java:263-265`). Do NOT let the FE reuse the admin force-reset (`/api/users/{id}/reset-password`) for self — it skips old-pw verification (`UserServiceImpl.java:189-195`) and is admin-only; using it for self would be a security hole.
2. **Phone uniqueness on self-update** — phone is the unique login identifier (`uq_users_phone`; login = `findByPhone`, `AuthServiceImpl.java:117`). Self phone-change MUST enforce uniqueness (excluding own row) — see §B. Same for email (`existsByEmail`, unique-nullable, informational — not used for login).
3. **Token validity after phone/email change — SURVIVES.** ✅ Token subject is the **user UUID**, not phone: access token `subject(principal.getId())` (`JwtTokenProvider.java:75`); refresh token `subject(userId)` (`:99`); the `phone` claim is explicitly *informational* and the auth filter keys off `sub` UUID (`JwtTokenProvider.java:34,76` comments confirm). The stale `phone` claim lingers only in the current 15-min access token and is never used for identity. ⇒ a phone/email change does **not** invalidate the session and needs no re-login. (Email isn't in any token claim at all.)
4. **Confirmation step for email/phone change — CTO PRODUCT CALL (flag only).** No email/SMS verification infra exists (external channels FCM/SMTP/SMS still stubbed — see backlog "Known tech-debt"). Options: (a) accept changes directly (simplest, matches current trust model where admin sets these anyway); (b) require current-password re-entry to change phone/email (cheap extra guard, no infra); (c) full OTP/verify-link (needs the stubbed channels — out of scope now). **Recommend (a) or (b); defer (c).** Not a code blocker — flagging for the ruling.

---

## §D — FE scope

- **Admin portal has NO profile page today.** Routing `frontend/apps/admin/src/App.tsx` has no `/profile` or `/me` route; nav `frontend/apps/admin/src/components/Layout.tsx:17-33` has no profile item. (The resident app *does* have `resident/src/pages/ProfilePage.tsx` — usable as a reference pattern, not shared code.)
- **New route — guarded to ALL authenticated roles.** Add e.g. `/profile` inside the `RequireAuth`/`Layout` block (`App.tsx:63-79`) **without** a `RequireRole` wrapper (or `RequireRole roles={['ADMIN','BOARD_MEMBER','TECHNICIAN']}`) — every other admin route is role-gated, profile must be the universal one.
- **Interaction with P2 role-aware routing/nav (backlog (c)).** This is the **first universal admin-portal surface**: today TECHNICIAN reaches only `/tickets` (`homePathFor.ts:14-17`, `App.tsx:69`). A profile route adds a second technician-reachable route. Order constraint from (c): the P2 "audit & tighten `RequireRole` on ALL pages" step must already be done/respected — the profile route is the one intentional all-roles exception, so it must be added *as such*, not by widening an existing gate.
- **Nav:** add a profile entry to `NAV` (`Layout.tsx:17-33`) with `roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN']` so it shows for everyone (nav is filtered by `n.roles.includes(user.role)`, `Layout.tsx:45`). A header/avatar menu by the existing `{user?.fullName}` block (`Layout.tsx:111`) is an alternative placement — CTO/UX call.
- **Page wiring:** read via `GET /api/auth/me`; password form → `PUT /api/auth/me/password` (live); profile form → new `PUT /api/auth/me/profile` (BE phase). Mirror resident ProfilePage UX + the UsersPage create-form `PHONE_ALREADY_EXISTS` handling.

---

## §E — Verdict

**NOT FE-only.** 2 of 3 features ship FE-only (me-read + change-password already exist and are live); **1 feature (self profile-update of phone/email/fullName) requires a small gated BE phase first.**

**Recommended phase breakdown:**
1. **BE phase (TDD, `ecc:springboot-tdd`)** — add `PUT /api/auth/me/profile` + `UpdateOwnProfileRequest` + `AuthService.updateOwnProfile`: authenticated-only, principal-derived id, phone/email uniqueness pre-checks (own-row-excluded), `PhoneUtils.normalize`, reuse validation + error codes. Small, single endpoint. Gate before FE. *(Confirm CTO ruling on §C.4 confirmation step before coding — it changes the DTO/flow only marginally.)*
2. **FE phase** — admin `ProfilePage` + all-roles `/profile` route + nav entry; wire all three features. Coordinate with backlog (c) P2/P3 routing work (profile = the universal-access exception).

**Open product question for CTO:** §C.4 — should email/phone changes require any confirmation (recommend: direct, or current-password re-entry; defer OTP).
