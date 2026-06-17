# (c) P1b — UsersPage smoke-test defect diagnosis + fix

Three defects from CTO smoke-test of P1 (admin UsersPage) on :80. Diagnosed at HTTP+DB before any code change. Backend untouched — all real causes are FE-only (or no-defect).

## Ground truth — `/api/users` contract (verified by reading source + live HTTP)

- `UserResponse` (list + POST/PUT body) serializes the active flag as **`isActive`** — `@JsonProperty("isActive") boolean isActive` (`UserResponse.java:33`). Live `GET /api/users` row keys: `id, email, fullName, phone, role, dateOfBirth, createdAt, isActive` — **no `active` key**.
- `GET /api/users` status filter param = **`isActive`** (`UserController.java:83`, `@RequestParam Boolean isActive`); service filters `cb.equal(root.get("active"), isActive)` (`UserServiceImpl.java:76-77`).
- `PUT /api/users/{id}` body = `UpdateUserRequest{fullName, phone, role, isActive}`; `isActive` is `Boolean @NotNull` (`UpdateUserRequest.java`), applied via `user.setActive(request.isActive())` (`UserServiceImpl.java:156`).
- `PUT /api/users/{id}/reset-password` body = `{newPassword}` (`ResetPasswordRequest.java`), `@Pattern` complexity enforced.

## D1 — edit cannot change isActive — **FE bug (key mismatch), NOT a BE bug**

**HTTP+DB proof (target = a RESIDENT, id 43200166…):**
- `PUT {…,isActive:false}` → **HTTP 200**; `GET /users/{id}` → `isActive=False`; DB `select is_active` → **`f`**. Restore `isActive:true` → DB **`t`**. So the BE PUT path persists `isActive` correctly with the JSON key `isActive`.

**Real cause (FE):** P1 `UsersPage` declared `StaffUserItem.active` and read `u.active` everywhere, but the API returns **`isActive`**. So:
- `openEdit` did `setIsActive(u.active)` → `undefined` → the edit toggle always initialised to "Đã vô hiệu hóa" regardless of the real state, and saving sent the wrong/false value.
- The list status column read `u.active` (`undefined`) → every row rendered inactive, so a successful PUT never visibly "took".

**Fix (FE only):** `StaffUserItem.active` → `isActive`; `openEdit` reads `u.isActive`; list column + self-row deactivate condition read `u.isActive`. The PUT payload already sent the correct `isActive` key — unchanged.

## D2 — status filter returns wrong/unfiltered results — **same FE key mismatch; filter param was already correct**

**HTTP proof:** `GET /users?isActive=false` returned the flipped row (`target present = True`) and `total=0` when no users are inactive; `?isActive=true` returned all 1753 active. The BE filter discriminates correctly and the FE already sends the correct param name `isActive` (`params.isActive = activeFilter`).

**Real cause:** not the query — the returned rows all rendered as inactive because the column read `u.active` (undefined). Fixed by the same `active`→`isActive` read correction. **No filter-param change made.**

## D3 — reset-password "unverified" — **NOT A DEFECT (no code change)**

**HTTP+DB proof:** `PUT /users/{id}/reset-password {newPassword:"NewPass@2026"}` → **HTTP 204**; DB `password_hash` changed `$2a$12$oyF7…` → `$2a$12$KuWx…`; subsequent `POST /auth/login` with the new password → **returned an accessToken (authenticated)**. Reset works end to end. (Dev account password then restored to the demo password `Demo@1234`.) The technician-login-blocked observation in the smoke note is EXPECTED — the FE role-gate (P3 not yet done) rejects TECHNICIAN at both portals; that is a role-gate effect, not a reset-password failure.

## Fix scope

FE-only, `frontend/apps/admin/src/pages/UsersPage.tsx`: `active` → `isActive` (interface + 4 reads). No BE change. No filter-param change. P1 ADMIN guardrail (confirm dialog on create/promote ADMIN) and self-row deactivate-hidden — untouched/preserved.

## Verification (post-fix)

- HTTP+DB (above) confirms BE persist + filter + reset all correct; the fixed FE now reads the same `isActive` key the API emits, so edit-toggle initial state, list display, and filtered display are all consistent with the DB.
- admin `tsc --noEmit` + `vite build`: **green** (587 modules). `grep '\.active\b'` in UsersPage → 0.
- Code-review: focused manual pass (5-line key rename, no logic/pattern change, guardrail + self-row-hide preserved). Full ECC multi-agent pass skipped given session cost-critical state and the trivial, HTTP-verified nature of the diff.
- **NOT browser-verified after fix — CTO re-smoke on :80 (`docker compose up -d --build nginx`).**
