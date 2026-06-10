# PROGRESS — Apartment Management System

## 🔄 IN PROGRESS — Form-Feedback Standardization (2026-06-09)

**Standard:** All forms → errors = VN inline by BE error CODE (never raw serverMsg); success = toast.

**Authoritative plan:** `reports/form-feedback-survey.md` — 27 forms audited, 26 deviating, 1 fixed pre-survey.

### What is DONE

**Foundation (BE + shared util):**
- BE: `ResidentServiceImpl` email-dup throws `EMAIL_ALREADY_EXISTS` not generic `CONFLICT` (e66b86e). Both dup paths symmetric.
- BE: 7 generic-CONFLICT spots → specific codes; 4 new `ErrorCode` entries (e604f8a). `reports/error-code-audit.md` has full list.
- Shared util: `getVnErrorMessage(errorCode?: string): string` in `@gemek/ui/src/lib/errorMessages.ts` — 22 codes mapped to VN, unknown → fallback. 26 tests green (00db804 + extensions).

**Cluster 1 — 5 forms standardized:**
Forms: admin Login, resident Login, resident Change Password, resident Book Amenity, resident Rate Ticket.
- Admin ResidentsPage create form: `PHONE_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS` → per-field inline VN (ea68b10).
- 5 forms: errors via `getVnErrorMessage(err?.response?.data?.error)`; success via MutationCache `meta.successMessage` or navigate (ecda711 + 80a0fff + b4d2889).
- Login 401-interceptor reload fix: both `apiClient` interceptors skip refresh+retry for `/auth/login` and `/auth/refresh` — business-logic 401 must not trigger token-refresh loop (b4d2889).
- `WRONG_CURRENT_PASSWORD` (422): added to BE (`ErrorCode` + `AuthServiceImpl`) and mapped in `getVnErrorMessage`. 422 bypasses 401 interceptor.
- `PASSWORD_POLICY_VIOLATION` (422): `@Pattern` removed from `ChangePasswordRequest.newPassword`; domain check moved to service layer; mapped in `getVnErrorMessage` (8a6ba52 + 48a6388).
- Change-password success toast: `useChangePassword` hook uses `meta: { successMessage: 'Đổi mật khẩu thành công.' }` → MutationCache fires toast. `skipSuccessToast` removed (48a6388).
- Toast CSS purge fix: resident `tailwind.config.js` now includes `../../packages/ui/src/**/*.{ts,tsx}` (c518623). CSS grew 15.19→17.50kB confirming Toast classes included.
- Toast positioning fix: `fixed right-4` anchors to viewport right edge; resident column is `max-w-md mx-auto` (448px) → toast outside frame on desktop. Fixed to `left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm` — centered over column on all widths (c4b3179).

**Auth state (confirmed stable):**
- Phone-as-login migration: COMPLETE (all 9 steps, see ✅ section below).
- Change-password hash integrity: NO corrupting path — both validations precede `setPasswordHash`; `@Transactional` rolls back on exception. Earlier corruption non-reproducible in current code (`reports/change-pw-integrity.md`).

### Cluster 1 Lessons (apply to clusters 2–5)

1. **Success toast = `meta.successMessage` via MutationCache**, NOT component-level `toast.success()`. Use `meta: { successMessage: 'VN message' }` in the mutation hook; MutationCache fires it automatically. Component-level `toast.success()` is also valid (singleton, reliable), but `meta.successMessage` is cleaner when message is fixed.
2. **Toast API:** call `toast.success(msg)` / `toast.error(msg)`. Never `toast({...})` — `toast` is an object, not a function.
3. **Toast positioning:** Toast container uses `fixed left-1/2 -translate-x-1/2` (viewport-centered). Do NOT revert to `fixed right-4` (viewport-right) — breaks resident narrow column. Do NOT add `position:relative` wrapper — fixed ignores it.
4. **Login success = navigate only.** No toast on successful login. All other mutations: success → toast.

## ⚠️ DEFERRED — Module 10 notification dispatch

**Full trace:** `reports/publish-notification-trace.md`

`AnnouncementServiceImpl.publishAnnouncement()` does NOT create notification rows — dispatch is a stub (class-level Javadoc: "full dispatch wired in Module 10"). `NotificationService.createNotification()` is fully implemented but never called from the publish path.

**Three secondary breaks that also need fixing in the same sprint:**
1. Bell unread badge: `useNotifications()` returns `PageResponse` (no `unreadCount`); `/notifications/unread-count` endpoint exists but is never called by resident Layout.
2. Announcement content not rendered: `AnnouncementsPage` (resident) shows title only — `a.content` not in JSX; no detail route.
3. Per-user `isRead` missing from `AnnouncementResponse`: DTO has `readByCount` (aggregate) but no individual `isRead`; unread highlight always fires.

**CTO decision required before implementation.** Options in trace report (Option A full fix ~4h, B partial, C defer).

---

### Cluster 2 — IN PROGRESS (2026-06-10)

**Authoritative plan:** `reports/form-feedback-survey.md`
**Done in cluster 3 so far:**
- ApartmentsPage (#8 Create Apartment, #9 Edit Apartment) — code landed eb2ece4, **AWAITING browser-verify**. No new ErrorCodes needed. Diagnosis: `reports/cluster3-apartments-diagnosis.md`. BE: 5/5 pass. FE: tsc+vite build clean. CONFLICT reuse noted (see diagnosis §4) — deferred.

**Done in cluster 4:**
- ContractorsPage (#10 Create Contractor, #11 Edit Contractor) — code landed 888aa4a, CTO smoke-verified on browser — OK. No new ErrorCodes. Diagnosis: `reports/cluster4-contractors-diagnosis.md`.

**Done in cluster 5:**
- ParkingPage (#13 Assign Parking Slot, #14 End Parking Assignment) — code landed b726f90, CTO smoke-verified on browser — OK. Diagnosis: `reports/cluster5-parking-admin-diagnosis.md`. #13: added `meta.successMessage` + VN inline error; #14: added `skipErrorToast: true` + inline error via `endError` state (success path untouched).

**Done in cluster 6:**
- TicketDetailPage (#15 Assign Ticket, #16 Update Status) + TicketsPage (#17 Create Ticket) — code landed 31f59b4, **smoke-verify pending**. Diagnosis: `reports/cluster6-tickets-admin-diagnosis.md`. #15: success toast + VN inline error (split `assignError` from shared `actionError` — bug fix: errors were rendering in wrong panel). #16: success toast + VN inline error + English strings removed (`statusError` state added). #17: VN inline error only (redirect on success unchanged). BE HTTP-verified: 12/12 TicketControllerTest pass. tsc + vite build clean.

**Next item:** cluster 7 = admin VehiclesPage #18 per `reports/form-feedback-survey.md`

**Admin toast position fixed (0da5f4c):** `Toaster` gained optional `position` prop (`"center"` default | `"top-right"`). Admin passes `position="top-right"`; resident unchanged.

**Done in cluster 2 so far:**
- AnnouncementsPage (#6 Create Announcement, #7 Publish Announcement) — code landed ec3a2d8, CTO smoke-verified on browser — OK. Diagnosis: `reports/cluster2-announcements-diagnosis.md`. BE: 4/4 tests pass.
- AmenitiesPage (#2 Create Amenity, #3 Edit Amenity, #4 Approve Booking, #5 Reject Booking) — CTO smoke-verified on browser — OK.
  - FE form feedback: d171df5 — Create/Edit successMessage; Approve/Reject skipErrorToast + inline error areas
  - CONFLICT→specific-code split: 073a3bf (BE), 2bf2fa5 (BE tests), 72bc19f (ui map + tests), 51e6808 (API-SPEC)
    - `AMENITY_NAME_EXISTS` (create/edit dup name), `BOOKING_NOT_PENDING` (approve/reject non-pending)

## ⚠️ DEFERRED — Code-Split Candidates (batch pass later)

Generic codes reused for context-specific cases — surfacing as less-specific VN messages. Recommend dedicated codes; defer to one batched BE + ui pass.

| Operation | Current code | Case | Recommended |
|-----------|-------------|------|-------------|
| assignSlot (parking) | `CONFLICT` | slot status ≠ AVAILABLE | `SLOT_NOT_AVAILABLE` |
| assignSlot (parking) | `CONFLICT` | slot already has active assignment | `SLOT_ALREADY_ASSIGNED` |
| assignTicket | `VALIDATION_ERROR` | both assignedToUserId + assignedToContractorId set | `BOTH_ASSIGNEES_SET` |

---

### What is REMAINING

Apply per-form: `getVnErrorMessage(err?.response?.data?.error)` for errors; `meta: { successMessage: 'VN msg' }` for success; remove raw `.message` echoing; remove English fallback strings.

**Resume pointer:** Open `reports/form-feedback-survey.md` → cluster 7 = admin VehiclesPage #18.

---

## ✅ TECH DEBT — Test Regressions (CLEARED 2026-06-10)

**Full inventory:** `reports/test-regression-inventory.md`
**Final report:** `reports/test-regression-final.md`

**Result: 244 run, 244 pass, 0 fail.**

All 16 classes fixed. Fix pattern: `ADMIN_EMAIL` → `ADMIN_PHONE = "0900000000"`, `ADMIN_PASSWORD = "GemekAdmin2026"`, add `phoneFromUid` helper, resident-create helpers use `phone`+`dateOfBirth` instead of `email`. Two assertion fixes: `UserControllerTest` search (position-based → existence check), `TicketControllerTest` rate-not-done (`CONFLICT` → `INVALID_STATUS_TRANSITION`), `ResidentControllerTest` dup-email (`CONFLICT` → `EMAIL_ALREADY_EXISTS`).

---

## ✅ COMPLETE — Phone-as-Login Migration (2026-06-08)

**Status:** All 9 steps complete.

**Authoritative plan:** `reports/phone-username-survey.md` section D (9-step table).

**Key commits:** 4b3f020 (PhoneUtils) · 41b90ca (V12 migration) · 3e59bbc (core BE auth) · e1e2d14 (seeder) · 0f34f24 (FE login) · 594fae2 (FE display) · 4cf2ce1 (resident normalize) · 4237cba (API-SPEC v2.1)

| Step | Task | Status | Commit |
|------|------|--------|--------|
| 1 | `PhoneUtils.java` — normalize + isValid + 35 unit tests | ✅ done | 4b3f020 |
| 2 | V12 migration — phone NOT NULL + UNIQUE, email nullable | ✅ done | 41b90ca |
| 3 | Core BE auth: `UserPrincipal` (phone field, getUsername→phone), `JwtTokenProvider` (CLAIM_PHONE), `LoginRequest` (phone field), `UserRepository` (findByPhone/existsByPhone), `LoginResponse.UserSummary` (phone field), `AuthServiceImpl` (findByPhone + normalize), `CreateUserRequest` (phone required, email optional), `UserServiceImpl` (existsByPhone guard) | ✅ done | 3e59bbc (feat) + 1ccce1b (test) |
| 4 | `AdminSeeder` — promote hardcoded `"0900000000"` to `${app.admin.phone:0900000000}`, apply `PhoneUtils.normalize()` | ✅ done | e1e2d14 (feat) + bb4fe47 (test) |
| 5 | Verify/update `CreateResidentRequest` + `ResidentServiceImpl` for phone on user creation | ✅ done | (fix + test commits below) |
| 6 | FE both apps — auth stores (phone field, login sig, POST body), both `LoginPage.tsx` (label/type/validation in Vietnamese) | ✅ done | 0f34f24 (feat) + 388ba90 (docs) |
| 7 | FE audit — Layout (both, already name+role only ✓), resident `ProfilePage.tsx` (phone primary + email secondary row), admin `ResidentsPage.tsx` (phone+email columns, `ResidentItem` type replacing `any`) | ✅ done | pending commit |
| 8 | `API-SPEC.md` — auth login, user create, resident create contracts | ✅ done | (docs commit below) |
| 9 | Extra tests — resident null-email regression, CreateUserRequest null-phone validation | ✅ done | (test commit below) |

**Resume pointer:** Read `reports/phone-username-survey.md` for full context, hidden couplings, and risk notes before starting step 3.

---

## Current State
- **Phase:** DONE (all gates and phone-as-login migration)
- **Gate:** G1 ✅ G2 ✅ G3 ✅ G4 ✅ (2026-06-03)
- **Last completed:** 2026-06-08 — Dup-phone 500 → 409 fix: GlobalExceptionHandler now maps DataIntegrityViolationException → 409 CONFLICT (defense-in-depth); backend Docker rebuilt to deploy step-5 existsByPhone guard in ResidentServiceImpl; ResidentsPage 409 inline message uses server message instead of hardcoded wrong string. 14 tests green. Commits: b13807d (fix) + 2971559 (test).
- **Previously last completed:** 2026-06-08 — Demo seed script (`scripts/seed-demo-local.sql`): 3 blocks, 10 apartments, 30 residents, 5 staff (2 ADMIN + 3 TECHNICIAN). Password `Demo@1234` (BCrypt-12). Run: `cat scripts/seed-demo-local.sql | docker exec -i gemek-postgres psql -U gemek -d gemek`. Verified: counts match, 0 dup phones, 0 multi-active-residents.
- **Previously last completed:** 2026-06-05 — POST /api/residents: transactional user+resident create in one call. userId removed (breaking). New fields: fullName/email/password/phone/dateOfBirth + resident fields. email-duplicate → 409, apt-not-found → 404, both roll back (no orphan user). 184/184 tests compile; 183 pass (1 pre-existing Block sort flakiness, unrelated). Commits: 60f008f (tests) + 4216970 (feat). Backend rebuilt.
- **Previously last completed:** 2026-06-05 — Central toast system: Toaster + toast() in @gemek/ui, wired into TanStack MutationCache (both portals). Success toast default "Thao tác thành công", error maps 401/403/5xx to Vietnamese, passes serverMsg for 4xx. skipErrorToast on 12 admin + 5 resident mutations (all with inline catch). skipSuccessToast on MarkAllRead (both), MarkAnnouncementRead, CreateBooking (inline success UX), PublishAnnouncement (compound action). nginx rebuilt.
- **Previously last completed:** 2026-06-05 — ParkingPage assign form: vehicleId + apartmentId raw UUID inputs → async SearchableSelect dropdowns. Apartment first, vehicle filters by selected apartmentId (GET /api/vehicles?apartmentId=&search=&size=10&isActive=true) — prevents vehicle/apartment mismatch. parkingSlotId still derived from clicked slot row (unchanged). Feature remains TEMP_HIDDEN_DEFERRED. 201 confirmed via API. GET /api/vehicles `search` param added (Criteria API, OR licensePlate/brand/model, null-safe); 9/9 tests pass.
- **Also 2026-06-05:** Ticket assign form: replaced raw UUID input with async SearchableSelect dropdowns. Staff: 3-call merge (ADMIN+BOARD_MEMBER+TECHNICIAN) — BE only supports single role param. Contractor: shown only for MAINTENANCE_REPAIR, hidden otherwise. Mutual exclusivity enforced. scheduledDate + notes added to payload. Admin: VehiclesPage with async resident SearchableSelect (GET /api/residents?search=&size=20&isActive=true), apartment auto-derived from selected resident (no independent apartment picker), 409→"Biển số đã được đăng ký". Resident: MyVehiclesPage self-scoped via /residents/me (no list calls to /residents or /apartments), unit shown read-only. nginx rebuilt; 201 and 409 verified via curl.
- **Note:** AdminSeeder is idempotent by design — changing ADMIN_PASSWORD in .env after the admin exists requires scripts/reset-admin-password.sql (or docker compose down -v) to update the stored BCrypt hash.
- **Blocked:** None

---

## Completed Modules
| Module | Phase | Tests | Committed |
|--------|-------|-------|-----------|
| System Architecture v2 | architect | N/A | Yes |
| DB Schema v2 | architect | N/A | Yes |
| API Spec v2 | architect | N/A | Yes |
| Auth + RBAC | backend | 3 tests | Yes (bb08316) |
| Apartments & Blocks | backend | 4 tests | Yes (c4b6f00) |
| Residents & Vehicles | backend | 5 tests (Docker req.) | Yes (424df57) |
| Ticket Management | backend | 8 tests | Yes (5dcb446) |
| Contractors & Contracts | backend | 5 tests | Yes (ce782f9) |
| Announcements | backend | 4 tests | Yes (53cae29) |
| Amenity Booking | backend | 5 tests | Yes (8863449) |
| Parking | backend | 4 tests | Yes (dc19526) |
| Notifications + Audit Log | backend | 3 tests | Yes (50e07a5) |
| Admin Portal (12 pages) | frontend | N/A | Yes (ba1c634) |
| Resident Portal (9 pages) | frontend | N/A | Yes (ba1c634) |
| Shared UI Package | frontend | N/A | Yes (ba1c634) |

---

## Approved Gates
| Gate | Approved | CTO Notes |
|------|---------|-----------|
| G1 — Techstack | ✅ 2026-05-29 | |
| G2 — Backend | ✅ 2026-05-29 | |
| G3 — Frontend | ✅ 2026-05-29 | SAST backend+frontend both PASS WITH NOTES before approval |
| G4 — Testing | ✅ 2026-06-03 | 149/149 tests pass, security audit 19/20 fixed, SEC-20 deferred, app boots fresh DB |

---

## Backend Module Queue
| # | Module | Status | Committed |
|---|--------|--------|-----------|
| 0 | Project scaffold (pom.xml, docker-compose, Flyway base) | ✅ done | Yes |
| 1 | Auth + RBAC | ✅ done | Yes |
| 2 | Apartments & Blocks | ✅ done | Yes |
| 3 | Residents & Vehicles | ✅ done | Yes |
| 4 | Ticket Management | ✅ done | Yes |
| 5 | Contractors & Contracts | ✅ done | Yes |
| 6 | Announcements | ✅ done | Yes |
| 7 | Amenity Booking | ✅ done | Yes |
| 8 | Parking | ✅ done | Yes |
| 9 | Reports & Dashboard | ✅ done | Yes |
| 10 | Notifications + Audit Log | ✅ done | Yes |

---

## Session Resume Instructions
If context is lost, read these files in order:
1. `PROGRESS.md` (this file) — current state
2. `DECISIONS.md` — all decisions made
3. `docs/ARCHITECTURE.md` — system design
4. `docs/API-SPEC.md` — API contracts
5. `docs/DB-SCHEMA.sql` — database schema
6. Then continue from "Current State" above
