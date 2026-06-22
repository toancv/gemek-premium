# Backlog (c) — Staff/Non-Resident User Management + TECHNICIAN Portal Lockout + Role-Overlap Feasibility

**Type:** READ-ONLY design investigation. No production code touched. Every claim cites `file:line` from source read on 2026-06-17. `[TODO: kiểm tra]` marks anything not directly verified.

---

## §0 — Working-tree status

- Branch: `deploy/local`; HEAD `4c193b6` (`docs(context): hardening sprint closed`).
- Tracked tree: **clean**. Only untracked scratch under `reports/` + `scripts/GenHash.java` (pre-existing, unrelated). Verified via `git status --short`.
- This turn adds exactly one file: this report.

---

## §1 — Role model surface

**The model is single-value, end-to-end. One user = exactly one role.**

| Layer | Location | Fact |
|-------|----------|------|
| Enum | `backend/.../module/user/entity/UserRole.java:13-26` | 4 values: `ADMIN`, `TECHNICIAN`, `RESIDENT`, `BOARD_MEMBER`. |
| DB type | `db/migration/V1__create_enums_and_users.sql:14` | `CREATE TYPE user_role AS ENUM (...)`. |
| DB column | `V1...:159` | `role user_role NOT NULL DEFAULT 'RESIDENT'` — single scalar column. |
| DB index | `V1...:171` | `CREATE INDEX idx_users_role ON users (role)`. |
| Entity | `UserRole` mapped `@Enumerated(EnumType.STRING)` per enum Javadoc (`UserRole.java:10-11`). `[TODO: kiểm tra]` exact `User.java` field annotation not re-read this pass. |
| Identity table comment | `V1...:150` | "Central identity table. All roles share this table." |

**Where `role` is read/written:**
- Create: `UserServiceImpl.createUser` → `user.setRole(request.role())` (`UserServiceImpl.java:119`); request field `CreateUserRequest.role` `@NotNull` (`CreateUserRequest.java:37-38`).
- Update: `UserServiceImpl.updateUser` → `user.setRole(request.role())` (`UserServiceImpl.java:155`); request field `UpdateUserRequest.role` `@NotNull` (`UpdateUserRequest.java:29-30`).
- Filter/list: `listUsers` Criteria `cb.equal(root.get("role"), role)` (`UserServiceImpl.java:73-74`).
- Seeder: `AdminSeeder` `existsByRole(UserRole.ADMIN)` guard (`AdminSeeder.java:75`) + `admin.setRole(UserRole.ADMIN)` (`AdminSeeder.java:94`).
- Repository role queries (the only two): `existsByRole(UserRole)` (`UserRepository.java:69`); `findActiveUserIdsByRole(UserRole)` via `@Query("... WHERE u.role = :role ...")` (`UserRepository.java:81-82`) — used by N3 dispatch.

**Role → JWT → back:**
- Claim key `CLAIM_ROLE = "role"` (`JwtTokenProvider.java:38`); embedded into the **access** token via `.claim(CLAIM_ROLE, extractRoleName(principal))` (`JwtTokenProvider.java:77`). `extractRoleName` reads the principal's first authority (`JwtTokenProvider.java:181`).
- **SEC-06 still TRUE (confirmed):** `JwtAuthenticationFilter.java:130-134` builds authorities from the **DB entity**, not the token claim — `new SimpleGrantedAuthority("ROLE_" + user.getRole().name())`, with the explicit comment "SEC-06 — use DB role instead of stale token claim". The granted-authorities collection holds **exactly one** `ROLE_<role>` authority. The `role` claim in the JWT is informational only.

> Single-authority assumption is baked into the authorities builder. A multi-role model would change `List.of(one authority)` → a collection, which propagates to every `hasRole`/`hasAnyRole` check below.

---

## §2 — Authorization surface (`@PreAuthorize` inventory)

**88 actual `@PreAuthorize` annotation sites** (91 textual matches across 14 files − 3 Javadoc references: `UserService.java:22`, `ContractorService.java:28`, `UserController.java:45`). All use `hasRole(...)` / `hasAnyRole(...)` / `isAuthenticated()` — i.e. the single-authority model.

Per-controller annotation counts (main source): Amenity 11, Vehicle 5, Resident 8, Contractor 13, Apartment 5, Block 4, Announcement 7, Report 5, Parking 12, Notification 1 (class-level), Ticket 11, User 6.

**Roles referenced in `@PreAuthorize` expressions:** `ADMIN`, `BOARD_MEMBER`, `TECHNICIAN`, `RESIDENT` (plus `isAuthenticated()`).

**Endpoints a TECHNICIAN is GRANTED by `@PreAuthorize` but cannot reach (no portal admits the role — see §3):**
- Tickets: list `TicketController.java:99`, detail `:180`, assign/claim `:227`, update status `:274`, + contractor-assign path `:252`.
- Parking: list `ParkingController.java:85`, slot detail `:125`, assign `:260`, unassign `:282`, guest log `:300`.
- Contractors: list `ContractorController.java:79`, detail `:118`, schedules `:212`, `:281`.
- Apartments/Blocks (read): `ApartmentController` has none for TECHNICIAN; `BlockController.java:70` grants TECHNICIAN block-list.
- Amenities (read): `AmenityController.java:93`, `:216`.
- Announcements (read): `AnnouncementController.java:81`, `:135`.

Net: ~20 endpoints across 6 modules are technician-authorized at the BE but **dead** because no FE portal will let a TECHNICIAN authenticate.

---

## §3 — FE role-gate (H5)

**Allowed-sets (hard-coded constants):**
- Admin app: `ALLOWED_ROLES = ['ADMIN', 'BOARD_MEMBER']` (`frontend/apps/admin/src/store/authStore.ts:32`).
- Resident app: `ALLOWED_ROLES = ['RESIDENT']` (`frontend/apps/resident/src/store/authStore.ts:32`).
- → **TECHNICIAN is in neither set → locked out of both portals at login AND at session-restore.**

**Two gate locations per app (identical pattern in both `authStore.ts`):**
1. **bootstrap** (session restore), after cookie-refresh + `GET /auth/me`: `if (!ALLOWED_ROLES.includes(meRes.data?.role))` → local reset to `unauthenticated`, `return` (admin `:65-68`, resident `:65-68`).
2. **post-login**, on the login-response user: `if (!user || !ALLOWED_ROLES.includes(user.role))` → local reset + `throw` an Error carrying `error: 'WRONG_PORTAL'` (admin `:82-87`, resident `:82-87`).

**WRONG_PORTAL handling:** thrown error shaped as `{ response: { data: { error: 'WRONG_PORTAL' } } }` so the existing `getVnErrorMessage` maps it (key present in `packages/ui/src/lib/errorMessages.ts`) → «Tài khoản không có quyền truy cập cổng này.» No hard-coded component string.

**Local-reset-not-logout rule (MUST PRESERVE):** on mismatch the store sets `accessToken/user = null`, `authStatus = 'unauthenticated'` and **never calls `/auth/logout`** — comment at `authStore.ts:63-64`: calling logout "revokes the user's refresh tokens and would kill their legitimate session in the other portal/tab". Any (c) change MUST keep: (1) role validated on BOTH gate locations, (2) mismatch → local reset only, (3) no `/auth/logout` on a wrong-portal hit. Reopening either reopens the H5 silent identity-switch hole.

**FE route/nav gates (separate from the store gate):**
- Admin `App.tsx:36-40` `RequireRole` redirects to `/dashboard` if `user.role` not in route's `roles`. Routes gated: apartments/contractors/reports `['ADMIN','BOARD_MEMBER']`, residents/announcements/vehicles `['ADMIN']`. `tickets` + `dashboard` have **no** `RequireRole` (any admitted role).
- **Inconsistency to flag:** admin `Layout.tsx` NAV already lists `TECHNICIAN` on dashboard (`:18`) and tickets (`:21`) nav items — i.e. the nav config anticipates a technician in the admin portal, but the store gate (`:32`) denies the role entirely. Dead config today; relevant to D2-Option-A.

---

## §4 — Existing user-management surface

**BE: full staff CRUD already exists, ADMIN-only.** `UserController` (`@RequestMapping("/api/users")`, `UserController.java:48`):

| Method | Endpoint | Auth | Line |
|--------|----------|------|------|
| List (role/active/search filters, paged) | `GET /api/users` | `hasRole('ADMIN')` | `:78-79` |
| Create (any role incl. TECHNICIAN/ADMIN/BOARD_MEMBER) | `POST /api/users` | `hasRole('ADMIN')` | `:109-110` |
| Get by id | `GET /api/users/{id}` | `hasRole('ADMIN')` | `:122-123` |
| Update (incl. role + active) | `PUT /api/users/{id}` | `hasRole('ADMIN')` | `:136-137` |
| Deactivate (soft, self-guard) | `DELETE /api/users/{id}` | `hasRole('ADMIN')` | `:152-153` |
| Reset password (admin force) | `PUT /api/users/{id}/reset-password` | `hasRole('ADMIN')` | `:169-170` |

- `CreateUserRequest` (`CreateUserRequest.java:24-46`): `email?`, `fullName`, `phone` (required), `role` (`@NotNull` — accepts ANY `UserRole`, no restriction), `password` (`@Pattern` complexity). So an ADMIN can already create a TECHNICIAN/BOARD_MEMBER/ADMIN account via the API today.
- `UpdateUserRequest` (`UpdateUserRequest.java:20-34`): `fullName`, `phone?`, `role`, `isActive`.
- Deactivate is soft (`user.setActive(false)`, `UserServiceImpl.java:178`) with self-deactivation guard → `SELF_OPERATION_NOT_ALLOWED` (`:172-175`). No hard delete.

**FE: NO admin user-management UI exists.** `frontend/apps/admin/src/pages/` has 12 pages (Login, Dashboard, Apartments, Residents, Tickets, TicketDetail, Contractors, Announcements, Amenities, Parking, Reports, Vehicles) — **no `UsersPage.tsx`**. No `/users` route in `App.tsx`. No `/users` nav item in `Layout.tsx`. ResidentsPage manages residents only (it creates a RESIDENT user + resident record inline — not staff).

**→ The gap for (c) is purely FE: a staff user-management page wiring the already-built `/api/users` endpoints.** No new BE endpoints are strictly required for basic staff CRUD.

---

## §5 — Security considerations to FLAG (not implement)

1. **Privilege-escalation surface on Create/Update:** `POST /api/users` + `PUT /api/users/{id}` let any ADMIN mint or promote to ADMIN with no second factor / no approval. If (c) surfaces this in the UI, flag whether ADMIN-creating-ADMIN needs a guardrail (e.g. only ADMIN may set `role=ADMIN`, or a separate confirmation). Currently unrestricted at the DTO (`CreateUserRequest.role` accepts any value).
2. **Audit logging asymmetry:**
   - Update logs role *changes* at WARN (SEC-04) with actorId from `SecurityContextHolder` (`UserServiceImpl.java:146-152`).
   - **Create does NOT emit an equivalent WARN** — it logs `INFO` "User created — id=, role=" (`UserServiceImpl.java:124`). Creating a privileged staff account is not WARN-audited the way a role change is. Flag for parity.
   - All mutating methods carry `@Auditable` (`createUser` `:97`, `updateUser` `:142`, `deactivate` `:167`, `resetPassword` `:188`). Per DECISIONS (2026-05-29) `AuditLogAspect` is a DEBUG-only stub — **no DB audit row is written**. So "audit" today = log lines only. `[TODO: kiểm tra]` whether audit_logs table/aspect was ever implemented since.
3. **Password issuance flow:** create requires an admin-chosen plaintext password meeting `@Pattern`; there is no "invite / set-your-own-password" flow and no forced first-login rotation. `resetPassword` sets a new plaintext too. Flag whether staff onboarding wants an invite token instead of admin-known passwords.
4. **`@PreAuthorize` is `hasRole('ADMIN')` only** for all of `/api/users` — BOARD_MEMBER (read-only role) cannot view the user list. If (c) wants BOARD_MEMBER to view staff, that's a deliberate widening.

---

## §6 — Topology constraint (close-out ruling, 2026-06-16)

Restated from DECISIONS close-out + PROGRESS H5 section:
- Two-portal **simultaneous** use is DESIRED.
- In prod this is enabled by **separate subdomains** → independent cookie jars (admin and resident refresh cookies never collide).
- **NEVER** a shared cookie `Domain`.
- Prod requires `cookie-secure=true` (`AUTH_COOKIE_SECURE`).
- The FE role-gate (§3) is **prod-valid defense-in-depth**, not just a dev-collision workaround.
- Dev caveat (H4): on `localhost:80`/`:81` cookies are host-scoped not port-scoped → the two apps overwrite each other's refresh cookie; testing needs two browser profiles.

**Interaction with D2:**
- **D2-Option A (extend admin portal):** zero new topology. Technician uses the existing admin subdomain/cookie jar. Reuses the admin auth pipeline; only the allowed-set + routes/nav change.
- **D2-Option B (dedicated technician portal):** a **3rd app → 3rd subdomain → 3rd cookie jar**, 3rd nginx serving target (current single nginx serves admin:80 + resident:81 — a 3rd port/server block), 3rd build pipeline, and its own H5-style role-gate (`ALLOWED_ROLES=['TECHNICIAN']`). More infra, but cleanest UX separation and no risk of widening the admin gate.

---

## §7 — D1 options (multi-role model)

| | (a) Single role + "two accounts" convention | (b) True multi-role (many-to-many) |
|---|---|---|
| **DB** | No change. `users.role` enum column stays. | New `user_roles` join table (user_id × role) OR `roles[]` array column; drop/relax `users.role` scalar; migrate `idx_users_role`; backfill existing rows. New migration V17+. |
| **Entity** | No change. | `User.role` → `Set<UserRole>` (`@ElementCollection`/`@ManyToMany`); update `@Enumerated` mapping. |
| **Auth pipeline** | No change. | `JwtAuthenticationFilter.java:134` single-authority → collection of `ROLE_*`; `JwtTokenProvider.extractRoleName` (`:181`, first-authority) + `CLAIM_ROLE` (`:77`) → multi-value claim. |
| **Authz** | No change. `hasAnyRole` already composes. | All **88 `@PreAuthorize`** sites still valid *semantically* (hasAnyRole works with multiple authorities) BUT must be re-audited — a user holding RESIDENT+ADMIN now passes resident-scoped IDOR guards AND admin guards simultaneously; server-side resident-scoping (DECISIONS 2026-06-04 IDOR pattern) must be re-checked. |
| **Repository** | No change. | `existsByRole` (`UserRepository.java:69`) + `findActiveUserIdsByRole` (`:81-82`) → join-aware queries; N3 dispatch recipient logic re-verified. |
| **DTO/API** | No change. | `CreateUserRequest.role`/`UpdateUserRequest.role` → `Set<UserRole>`; `UserResponse`/`/auth/me` role → array; API-SPEC rewrite. |
| **FE** | No change. | `AuthUser.role: string` → `string[]` in BOTH `authStore.ts`; `ALLOWED_ROLES.includes(role)` → set-intersection in 4 gate locations; `RequireRole` (`App.tsx:38`) + `Layout` nav filter (`Layout.tsx:42`) → "any-of" logic; everywhere reading `user.role`. |
| **Seeder** | No change. | `AdminSeeder.existsByRole` / `setRole` → multi-role aware. |
| **Effort** | **XS** — operational convention + (optionally) docs. The staff-CRUD UI (§4) is the only real work and is shared with the "do nothing to the model" path. | **L–XL** — schema migration + auth pipeline + DTO/API + FE type change + re-audit of all 88 authz sites and every IDOR-scoping guard. |
| **Risk** | Low. Cost: a human who is both resident and technician needs two logins/phones (phone is the unique login identifier — `uq_users_phone`); UX annoyance, no security risk. | High. Touches the security core (authority mapping + every guard). Re-opens IDOR-scoping questions. High regression surface across BE+FE+DB. |

**Single biggest surface multi-role touches:** the **authorization core** — the single-authority→collection change at `JwtAuthenticationFilter.java:134` cascading into all **88 `@PreAuthorize`** sites and every server-side resident-scoping/IDOR guard, which must each be re-audited for "user holds RESIDENT *and* a staff role at once".

**Evidence lean (not a mandate):** the model is single-value with no current product requirement for one human to act in two roles in one session; today's gap (c) is fully solvable in option (a) (just build the staff-CRUD UI). Multi-role is a security-core rewrite for a need not yet demonstrated. Recommend CTO treat (b) as deferred unless a concrete "same person, two roles, one session" requirement is confirmed.

---

## §8 — D2 options (where TECHNICIAN works)

| | (A) Extend admin portal | (B) Dedicated technician portal |
|---|---|---|
| **Auth gate** | Add `'TECHNICIAN'` to admin `ALLOWED_ROLES` (`admin/authStore.ts:32`) — both bootstrap + login gates inherit it. | New app with `ALLOWED_ROLES=['TECHNICIAN']`; admin gate unchanged. |
| **Routing** | Add technician-scoped routes + `RequireRole roles={['TECHNICIAN', ...]}` on the few pages they may see (tickets already ungated; restrict others). Wire nav (`Layout.tsx` already lists TECHNICIAN on dashboard `:18` + tickets `:21` — dead today). | Full new app shell: routes, layout, nav, login, store. |
| **UI scope** | Reuse existing TicketsPage/TicketDetailPage; hide admin-only pages from technician via `RequireRole` + nav filter. Must verify no admin-only page leaks (residents/announcements/users/reports). | Build only the technician surfaces (tickets queue, assigned work, parking if relevant per §2). |
| **Topology** | Zero new infra — same admin subdomain + cookie jar (§6). | 3rd subdomain + 3rd cookie jar + 3rd nginx server block + 3rd build (§6). |
| **Risk** | Widening the admin gate means a misconfigured `RequireRole`/nav could expose admin-only data to a technician. Must audit every admin page's `RequireRole` after adding the role. Keeps H5 invariants intact (gate logic unchanged, only the set grows). | Clean separation, no admin-exposure risk, but most infra + build cost; another portal to maintain + secure. |
| **Effort** | **S–M** — gate + per-page RequireRole audit + nav. | **L** — new app + infra + deploy. |

Both options must preserve all §3 H5 invariants. Neither option requires the D1 multi-role change — a technician is a single-role user in either.

---

## §9 — Open questions for CTO ruling

1. **D1:** Single-role + two-account convention (a), or true multi-role many-to-many (b)? (Evidence leans (a) — see §7; confirm no "one person, two roles, one session" requirement exists.)
2. **D2:** Extend admin portal (A) or dedicated technician portal (B)? If (A), confirm the per-page `RequireRole` audit is in scope.
3. **Staff-CRUD UI scope:** build the missing admin UsersPage over the existing `/api/users` endpoints (§4) — confirm which staff roles it manages (ADMIN/TECHNICIAN/BOARD_MEMBER) and whether BOARD_MEMBER gets read access (currently ADMIN-only).
4. **Privileged-account guardrail:** should ADMIN-creating-ADMIN (or any promotion to ADMIN) require an extra guard/confirmation? (§5.1)
5. **Audit parity:** add WARN + real audit row on staff *creation* to match the role-*change* WARN (SEC-04)? Note `AuditLogAspect` is still a DEBUG stub — does (c) require finishing the audit_logs persistence? (§5.2)
6. **Onboarding/password flow:** admin-set plaintext password (current) vs invite-token / forced first-login rotation? (§5.3)
7. **Technician scope confirmation:** which of the ~20 technician-authorized BE endpoints (§2) should actually surface in the technician UI (tickets only, or also parking/contractors)?
