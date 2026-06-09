# PROGRESS — Apartment Management System

## 🔄 IN PROGRESS — Form-Feedback Standardization (2026-06-09)

**Standard:** All forms → errors = VN inline by BE error CODE (never raw serverMsg); success = toast.

**Authoritative plan:** `reports/form-feedback-survey.md` — 27 forms audited, 26 deviating, 1 fixed.

**What is DONE:**
- BE distinct dup codes NOW CONFIRMED BOTH PATHS: `ResidentServiceImpl` was throwing `CONFLICT` for email-dup (bug fixed e66b86e); `UserServiceImpl` was already correct. Both now throw `EMAIL_ALREADY_EXISTS` (409).
- Email address no longer leaked in ResidentServiceImpl error message.
- Admin ResidentsPage create form fixed (ea68b10): `PHONE_ALREADY_EXISTS` → `setPhoneError("Số điện thoại đã được sử dụng.")`, `EMAIL_ALREADY_EXISTS` → `setEmailError("Email đã được sử dụng.")`, unknown 409 → generic VN; raw serverMsg no longer echoed
- Survey complete: `reports/form-feedback-survey.md`
- BE error code audit complete: `reports/error-code-audit.md` — 20 codes (16 specific, 4 generic); CONFLICT gaps patched (e604f8a, 7 spots, 4 new enum entries)
- `getVnErrorMessage(errorCode?)` util in `@gemek/ui/src/lib/errorMessages.ts` — maps all 20 BE codes + fallback; 24 tests green; extended (00db804)
- **Cluster 1 done (ecda711):** 5 forms standardized — admin LoginPage, resident LoginPage, resident ProfilePage (change-password), resident AmenitiesPage (book), resident TicketDetailPage (rate). All use `getVnErrorMessage(err?.response?.data?.error)`; success actions fire VN toast; no raw `.message` or English errors remain.

**What is REMAINING:**
- Apply form-feedback standard to remaining 21 deviating forms (see `reports/form-feedback-survey.md` priority list)
- Cluster plan: use `getVnErrorMessage` from `@gemek/ui` in each form's catch block; add success toast where missing; fix silent-error handlers

**Resume pointer:** Cluster 1 complete (5 forms). Next = cluster 2: patch remaining 21 forms. Reference `reports/form-feedback-survey.md`.

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
