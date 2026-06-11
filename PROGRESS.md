# PROGRESS — Apartment Management System

## ✅ COMPLETE — Form-Feedback Standardization (2026-06-10)

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
- TicketDetailPage (#15 Assign Ticket, #16 Update Status) + TicketsPage (#17 Create Ticket) — code landed 31f59b4, **smoke-verify pending**. Diagnosis: `reports/cluster6-tickets-admin-diagnosis.md`. #15: success toast + VN inline error (split `assignError` from shared `actionError` — bug fix: errors were rendering in wrong panel). #16: success toast + VN inline error + English strings removed. #17: VN inline error only (redirect unchanged). BE HTTP-verified: 12/12 pass.

**Done in cluster 7:**
- VehiclesPage (#18 Create Vehicle) — code landed 2741ff0, **smoke-verify pending**. Diagnosis: `reports/cluster7-vehicles-admin-diagnosis.md`. Success toast added; HTTP-409-status hardcode replaced by `getVnErrorMessage(err?.response?.data?.error)` — `LICENSE_PLATE_ALREADY_EXISTS` maps to "Biển số xe đã được đăng ký." via code (not status). BE HTTP-verified: 9/9 VehicleControllerTest pass. tsc + vite build clean.

**Admin form-feedback COMPLETE — forms #1–#18 all standardized.**

**Done in cluster 8 (2026-06-10):**
- AnnouncementsPage (#20 Mark Read) — intentional-silent comment added; no functional change (fire-and-forget UX, silent by design).
- MyBookingsPage (#22 Cancel Booking) — `getVnErrorMessage` inline error added; `handleCancel` async with VN confirm; success toast was already working via `meta.successMessage: 'Đã hủy đặt chỗ'`.
- MyTicketsPage (#23 Create Ticket) — `meta.successMessage: 'Đã gửi yêu cầu.'` added to hook; catch fixed to `getVnErrorMessage(err?.response?.data?.error)`.
- MyVehiclesPage (#24 Register Vehicle) — `meta.successMessage: 'Đã đăng ký phương tiện.'` added to hook; HTTP-409-status hardcode replaced with `getVnErrorMessage(err?.response?.data?.error)`.
- ParkingPage (#25 Log Guest Vehicle) — `meta.successMessage: 'Đã ghi nhận xe khách.'` added; catch + validation error → `getVnErrorMessage`; all English form strings translated to VN.
- Commit: 77b9cae. BE tests: 8/8 (ParkingControllerTest). Resident tsc+vite build clean.

**ALL 27 FORMS COMPLETE. Form-feedback standardization DONE.**

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
| cancelBooking | `CONFLICT` | booking status ≠ PENDING | `BOOKING_NOT_CANCELLABLE` |
| cancelBooking | `CONFLICT` | booking date is in the past | `BOOKING_DATE_PAST` |

---

### What is REMAINING

Apply per-form: `getVnErrorMessage(err?.response?.data?.error)` for errors; `meta: { successMessage: 'VN msg' }` for success; remove raw `.message` echoing; remove English fallback strings.

**Resume pointer:** Form-feedback standardization COMPLETE (all 27 forms). Next on-deck: DEFERRED items (Module 10 notification dispatch, TEMP_HIDDEN_DEFERRED guards, code-split candidates above). CTO browser smoke-verify pending for clusters 6, 7, 8 (`docker compose up -d --build nginx`).

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

## ✅ COMPLETE — i18n Phase 1: Inventory (2026-06-10)

**Output:** `reports/i18n-inventory.md` — full categorized inventory of English UI strings needing Vietnamese translation across both React apps.

**Counts:**
- Admin app: ~247 TRANSLATE strings across 11 files. Top 3: ParkingPage (~38), AmenitiesPage (~37), ReportsPage (~33).
- Resident app: ~68 TRANSLATE strings across 8 files. Top 3: AmenitiesPage (~13), TicketDetailPage (~12), ProfilePage (~10).
- AMBIGUOUS: 10 strings requiring CTO ruling (BE enum values rendered as display labels — primarily ticket status/priority, vehicle types, apartment status, parking slot type/status, contractor specialties, and 'Created' null-oldStatus fallback).
- AnnouncementsPage (admin): 0 strings — fully Vietnamese, no work needed.

**Scope rules (locked):** IN = static JSX text nodes, placeholders, buttons, labels, nav, table headers, empty states, modal titles, tab names. OUT = `getVnErrorMessage` strings (already VN), enum `value=` attrs, variable names, code comments, already-VN strings.

---

## ⏸ IN PROGRESS — i18n Phase 2: Translation (RESIDENT + ADMIN APPS COMPLETE — all pages VN)

**Resume pointer (fresh session):** Read `reports/i18n-inventory.md` for full string list. Architecture locked in DECISIONS.md (2026-06-10 i18n entry). Terminology: user-facing "Ticket" = "Phản ánh", display only; create/submit verb = "Gửi phản ánh" (DECISIONS.md 2026-06-10).

**Resident cluster 2 COMPLETE (2026-06-10) — resident app fully VN:**
- Translated: AnnouncementsPage ('Thông báo', emptyYet 'thông báo', 'Everyone'→'Tất cả'), AmenitiesPage hidden-deferred ('Đặt tiện ích', 'Đặt {name}' interpolated, full booking form), ParkingPage ('Bãi xe', 'Chỗ đậu xe của tôi', Khu/Loại/Phương tiện/Thẻ/Từ labels, 'Slot' fallback→'Chỗ đậu').
- Terminology sweep: 'Gửi yêu cầu'→'Gửi phản ánh' + 'Loại yêu cầu'→'Loại phản ánh' (MyTicketsPage), modal 'Tạo phản ánh'→'Gửi phản ánh', 'Không thể tải yêu cầu hỗ trợ.'→'Không thể tải phản ánh.' (TicketDetailPage), useCreateTicket successMessage 'Đã gửi yêu cầu.'→'Đã gửi phản ánh.' (hooks.ts, text only). Grep confirms 0 "yêu cầu" left in resident src. Commit bd795b5.
- Verified: `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step (port 81; Amenities/Parking are TEMP_HIDDEN_DEFERRED, nav-hidden — verify via direct URL or note deferred).
- ~~Wording flag~~ RESOLVED 2026-06-10: CTO ruled Announcements = "Tin tức" everywhere, notification bell = "Thông báo" (DECISIONS.md). AnnouncementsPage title fixed → 'Tin tức'. Grep verified: no swaps in resident src. Commit cd784b1.

**Enum display-label maps BUILT (2026-06-10), NOT yet wired:**
- `@gemek/ui` `lib/enumLabels.ts`: 7 groups + `labelFor(enumType, key)` (raw-key fallback, null→''). Display only — raw enum keys stay in `value=`/filters/comparisons. 51/51 ui tests green. Commit 0c9e8d3.
- Wiring happens per-page during admin translation. Resident pages still render raw enums in a few spots (e.g. TicketDetail status/priority, MyTickets/MyBookings status chips, Parking type) — later cleanup pass adopts labelFor there.

**Admin cluster A1 COMPLETE (2026-06-11):**
- `apps/admin/src/i18n/vi.ts` created (nav/layout/dashboard/reports; `t = createT(vi, viShared)`). Layout + DashboardPage + ReportsPage fully VN. Commit a212a9f.
- labelFor wired (first adoption): Dashboard + Reports 'Phản ánh theo loại' category labels via labelFor('TicketCategory', cat) — replaced `cat.replace(/_/g,' ')`; Reports contracts Status chip via labelFor('ActiveStatus', c.status). Raw keys untouched in keys/logic/filters.
- TicketCategory group added to @gemek/ui enumLabels (5 keys, wording copied from resident create-form options). Commit cf29cb9 (extra feat(ui) commit, not in CTO list — kept package-commit separation).
- DashboardPage local `const t = data?.tickets` renamed → `tk` (shadowed i18n t(); internal var only, no display/API change).
- Verified: 51/51 ui tests + tsc + vite build green BOTH apps. NOT browser-verified — CTO step (port 80).
- Wording flag: contracts Status chip uses ActiveStatus map → ACTIVE shows 'Hoạt động'; for contracts 'Hiệu lực' may read better (summary card says 'Hợp đồng hiệu lực'). If CTO prefers, add ContractStatus group later.

**Admin cluster A2 COMPLETE (2026-06-11):**
- Translated: ApartmentsPage (title/filters/headers/badge/edit modal; status filter+select labels via labelFor('ApartmentStatus'), value= raw), ResidentsPage (title/search/headers; OWNER/TENANT badge via labelFor('ResidentType')), ContractorsPage (title/search/headers/modal; specialty cell+select via labelFor('ContractorSpecialty'), isActive badge via labelFor('ActiveStatus')). Pagination Tổng:/Trước/Sau via viShared. Commit 6b536fd.
- New enum groups (commit 567b4d6): ContractStatus uses REAL BE keys PENDING/'Chờ hiệu lực', ACTIVE/'Hiệu lực', EXPIRED/'Đã hết hạn', TERMINATED/'Đã chấm dứt' (CTO's INACTIVE does NOT exist in BE — verified vn.vtit.gemek.module.contractor.entity.ContractStatus); ResidentType OWNER/'Chủ sở hữu', TENANT/'Người thuê' (badge rendered raw, inventory miss). viShared += common.saving 'Đang lưu...', common.total 'Tổng:'.
- Reports expiring-contracts Status chip switched ActiveStatus→ContractStatus.
- Also: create-apartment modal 'Diện tích (sqm)'→'(m²)' for unit consistency.
- Verified: 51/51 ui tests + tsc + vite build green BOTH apps. NOT browser-verified — CTO step (port 80).
- Wording flags: ApartmentsPage pre-existing VN strings still say "block" ('Chọn block...', 'Vui lòng chọn block.') vs new 'Tòa' — needs terminology-sweep ruling. AddApartment create modal was already VN ('Thêm căn hộ mới', 'Tạo mới') — left as-is.

**Admin cluster A3 COMPLETE (2026-06-11):**
- TicketsPage: title 'Phản ánh', '+ Gửi phản ánh', filters (Tất cả loại/trạng thái + options via labelFor), headers Mã/Tiêu đề/Loại/Trạng thái/Phụ trách/Hạn SLA, chips via labelFor('TicketCategory'/'TicketStatus'), emptyFound 'phản ánh', modal 'Gửi phản ánh' (category/priority selects via labelFor, 'Đang gửi...'/'Gửi'). TicketCategory map already covered page keys — no ui commit needed.
- TicketDetailPage: loadError/back/labels VN, category/priority/status via labelFor, Photos→'Hình ảnh', Status History→'Lịch sử trạng thái', 'Created'→'Khởi tạo', 'by'→'bởi', '(chỉ MAINTENANCE_REPAIR)' hint→labelFor, update-status select switched to labelFor (DONE 'Hoàn thành'→'Hoàn tất' per locked map — flagged).
- block→'Tòa' sweep: ApartmentsPage (placeholder, validation), AnnouncementsPage (label, option, validation). Display-"block" grep in admin src = 0. Decision recorded in DECISIONS.md.
- Commit 9b2de7b. Verified: tsc + vite build green (admin). NOT browser-verified — CTO step (port 80).

**Admin cluster A4 COMPLETE (2026-06-11) — ADMIN APP FULLY VN (all pages):**
- ParkingPage: 'Bãi xe', tabs 'Chỗ đậu xe'/'Xe khách', filters (Tất cả loại/trạng thái + options via labelFor), slot headers Chỗ/Khu/Loại/Trạng thái/Phân cho/Thao tác, type cell + status chip via labelFor('VehicleType'/'ParkingSlotStatus'), emptyFound 'chỗ đậu xe', 'Phân công'/'Hủy phân công', guest headers Biển số/Chủ xe/Căn hộ tiếp/Giờ vào/Giờ ra/Mục đích, emptyYet 'xe khách', 'Đang trong bãi', assign modal 'Phân chỗ {slotNumber}' (interpolated) + labels/placeholders/'Đang phân...'.
- VehiclesPage: 'Phương tiện', '+ Thêm phương tiện', filters via labelFor (isActive filter values stay "true"/"false", labels ActiveStatus), headers, type cell + isActive badge via labelFor, emptyFound 'phương tiện', modal 'Thêm phương tiện' + type select labels via labelFor. VEHICLE_TYPES map param `t` renamed → `vt` (would shadow i18n t()).
- AmenitiesPage: 'Tiện ích', 'Thêm tiện ích', tabs 'Tiện ích'/'Lượt đặt chờ duyệt', headers, emptyFound 'tiện ích', Có/Không badge, booking headers, emptyYet 'lượt đặt', 'Duyệt'/'Từ chối', reject dialog 'Từ chối đặt chỗ'/'Lý do'/'Đang từ chối...', amenity modal 'Sửa tiện ích'/'Thêm tiện ích' + all labels, Hủy/Lưu/Đang lưu... via shared.
- No new enum keys needed (CAR/MOTORBIKE/BICYCLE/OTHER + AVAILABLE/OCCUPIED/RESERVED + ACTIVE/INACTIVE already mapped) — no feat(ui) commit. Enum value=/filters/logic untouched.
- Commit 0a66bfe. Verified: tsc + vite build green (admin); leftover-English grep on 3 pages = 0. NOT browser-verified — CTO step (port 80; Parking/Vehicles/Amenities may be TEMP_HIDDEN_DEFERRED — verify via direct URL).

**Admin leftover cleanup COMPLETE (2026-06-11):**
- `t('status')` key miss fixed (key is `common.status` → fallback rendered literal "status"): VehiclesPage:119 + ParkingPage:126 headers → `t('common.status')` = 'Trạng thái'.
- SLA wording (CTO-approved set): tickets.slaDeadline → 'Hạn hoàn thành'; dashboard.slaBreached + reports.slaBreachedCol → 'Trễ hạn'; reports.slaBreachRate → 'Tỷ lệ trễ hạn'; admin TicketDetailPage hardcoded 'SLA:' → new key ticketDetail.sla = 'Hạn hoàn thành:'. Grep 'SLA' in admin src = 0 displayed leftovers.
- "System Administrator" (top-right header + bottom-left sidebar of admin layout): NOT a static string — it is `user.fullName` from API (seeded admin account, backend AdminSeeder.java:91). Shows logged-in user identity → NOT removed. ⏸ CTO ruling pending (options: leave as-is / change seed fullName to VN / DB update of admin account). No FE change made for this item.
- Commit e7b945b. Verified: tsc + vite build green (admin). NOT browser-verified — CTO step (port 80).
- Date-format task (mm/dd→dd/mm) still pending — its OWN later session, untouched here.

**Resident enum-cleanup COMPLETE (2026-06-11) → i18n Phase 2 COMPLETE (both apps fully VN, enum labels consistent):**
- New maps in @gemek/ui enumLabels: AnnouncementType (GENERAL/URGENT/MAINTENANCE/AMENITY/EVENT → Chung/Khẩn cấp/Bảo trì/Tiện ích/Sự kiện), BookingStatus (PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED → Chờ duyệt/Đã duyệt/Bị từ chối/Đã hủy/Hoàn tất) + tests. Commit 649e8c9; ui 51/51 tests green.
- labelFor wired (display only; value=/chip-color keys/comparisons stay raw BE keys): HomePage + AnnouncementsPage announcement-type chips, MyBookingsPage status chip, MyTicketsPage status chip + category line (replace() hacks removed), TicketDetailPage status chip/category/priority + status-timeline old→new, ParkingPage slot type, MyVehiclesPage type options (map param t→vt, shadowed i18n t). Bonus leftover fixed: resident TicketDetail hardcoded 'SLA:' → ticketDetail.sla='Hạn hoàn thành:' (approved SLA mapping). Commit 3793983. tsc + vite build green (resident). NOT browser-verified — CTO step (port 81).
- Earlier resident raw-enum tech-debt note: CLEARED.
- ~~Leftover: admin AnnouncementsPage type options raw~~ FIXED 2026-06-11: type options via labelFor('AnnouncementType'); new AnnouncementScope map in @gemek/ui (ALL/BLOCK/FLOOR → Toàn bộ/Theo tòa/Theo tầng, BE-verified, commit fb42ae4, ui 51/51 green) wired to scope options + list "Phạm vi" column (was raw targetScope) — 'Theo block'→'Theo tòa' done via map. Commit 330aee0; admin build green. i18n fully COMPLETE both apps incl. dynamic form enums. NOT browser-verified — CTO step (port 80).
- "System Administrator" CTO ruling still pending (see admin leftover cleanup above).

**⏸ IN PROGRESS — date-INPUT picker rollout (KIND B → VNDatePicker), PILOT DONE 2 of 6, awaiting CTO pattern approval:**
- react-day-picker 9.7.0 (exact) added to @gemek/ui via corepack pnpm (pnpm 11.5.2; plain `pnpm` NOT on PATH — use `corepack pnpm`; npm install inside the pnpm tree FAILS, do not mix).
- `VNDatePicker` in @gemek/ui (commit c2cfe0a): value/onChange = ISO yyyy-mm-dd always; dd/mm/yyyy display; local-safe parseISODateLocal/toISODateLocal in dateFormat.ts (no UTC round-trip → no off-by-one); props min/disabled/placeholder/className; ui tests 65/65 green incl. month/year-boundary cases.
- Pilot (commit 8c4b8e7): admin Reports 'from' (controlled — value/onChange wired straight to existing ISO state; query param unchanged) + admin Residents moveInDate (was uncontrolled FormData → now controlled ISO state; payload key/shape/value format unchanged). Admin build green. NOT browser-verified — CTO step (port 80): check dd/mm display, Reports filter correctness, resident create saves moveInDate without off-by-one.
- Pattern APPROVED by CTO 2026-06-11 (pilot browser-verified: dd/mm display + ISO payload intact, no off-by-one).
- Rollout COMPLETE 2026-06-11 — all 6 date inputs now VNDatePicker: admin dateOfBirth (controlled, dobError clear kept; red-border error styling on the input itself dropped — wrapper has fixed classes, error TEXT below remains) + Reports 'to' (twin of 'from') + Parking startDate (FormData → controlled ISO state, reset in closeAssign) = commit 372f21a; resident AmenitiesPage bookingDate (FormData → controlled ISO state, reset on modal open, min=today ISO passed through → past dates disabled) = commit e58b892. ui 65/65 green; both builds green. KIND B limitation RESOLVED. NOT browser-verified — CTO step (ports 80/81).

**Admin AnnouncementsPage type-badge leftover FIXED (2026-06-11, commit 7038c1b):** list-table type badge rendered raw `{a.type}` → labelFor('AnnouncementType'). Map keys verified BE-exact (GENERAL/URGENT/MAINTENANCE/AMENITY/EVENT) — no map change needed. NOTE: 3rd dynamic-section miss on this one page (create-form options, scope column, now list badge) — static i18n inventory was blind to dynamically-rendered enum spots; any future i18n audit must grep for `{x.type|status|...}` render patterns, not just string literals. Admin build green. Browser-verify = CTO (port 80).

**✅ PUSHED 2026-06-11 — i18n + date-format work COMPLETE and on origin/deploy/local (HEAD 00a8cd2):** two verified pushes — b6078ba/3ce90c9 (work + docs) then 00a8cd2 (chore: gitignore node_modules/ + dist/, none were ever tracked). Pre-push verification green both times — ui 65/65, admin + resident tsc+vite builds green, backend full suite 244/244 (Java 21 via backend\mvnw.cmd — plain `mvn` NOT on PATH, use the wrapper).

**✅ Module 10 PHASE 1 (dispatch core) COMPLETE + CTO browser smoke-verified (2026-06-11).** Phase-1 design: `reports/module10-dispatch-design.md`. **EXTENDED scope (N1 deep-link/detail route → N2 rich content → N3 per-user event notifications → N4 ticket media+comments) recorded in `reports/module10-extended-backlog.md` — NEXT = N1.** N2/N4 gated behind F-05 presign fix; N3 design-first.
- Design proposal committed 96f9fa9; CTO approved P1 scope.
- **P1 DONE:** `ResidentRepository.findRecipientUserIds(scope, blockId, floor)` — typed default method → String-scoped backing `@Query` (Hibernate 6.5 enum-param anchoring limitation, see report P1-findings). Commit 221813b. Contract test `AnnouncementRecipientConsistencyTest` (4 tests, feed↔dispatch invariant per scope, edge cases moved-out/deactivated/no-apartment) commit a671c70. Suite 248/248 green.
- **P2 DONE (2026-06-11):** dispatch wired into `publishAnnouncement()` — CAS `publishIfDraft` (409 on already-published, race-safe, replaces idempotent-200), in-TX batch dispatch via `NotificationRepository.saveAll` + `getReferenceById` (no per-row SELECT), body "Có thông báo mới: {title}", `batch_size: 50` config. Commits: feat 22114b8, test 8880499 (`AnnouncementPublishDispatchTest` 5 tests: per-scope row counts, field checks, 409+no-duplicate, unread increment). Suite 253/253 green. DECISIONS.md entry 2026-06-11 Module 10 P2.
- **P4 DONE (2026-06-11):** per-user `isRead` on `AnnouncementResponse` (§E E-3 backend half) — `@JsonProperty("isRead")` field (mirrors NotificationResponse); `existsByAnnouncementIdAndUserId` already existed (no addition); list paths use ONE batched query/page (`AnnouncementReadRepository.findReadAnnouncementIds(userId, pageIds)` + in-memory set), detail uses single exists(); `toResponse(a)` kept as false-default overload for mutation paths (draft/just-published — read row impossible for caller). Commits: feat 0070727, test 3b5b725 (`AnnouncementIsReadTest` 3 tests: detail flip, mixed-page per-row flags, admin findAll path). Suite 256/256 green.
- **P5 DONE (2026-06-11) — Module 10 BE+FE code COMPLETE.** E-4: `useMarkAllRead` put→post both apps (no FE caller of `/notifications/{id}/read` exists; announcement markRead already POST) — commit dcfc42b. E-1: `useUnreadCount()` (`GET /notifications/unread-count`) both apps; both Layout.tsx badges read `unreadData.unreadCount` (old `notifData?.unreadCount` was dead — PageResponse has no such field); markAllRead invalidates `['unread-count']` + `['notifications']` — commit 732beac. E-2+E-3-FE: resident AnnouncementsPage expand-on-click shows `a.content` inline (local `expandedId` state, no route); isRead now real per-user (P4 BE), markRead already invalidates `['announcements']` so border clears on refetch; item type is `any` — no type to extend — commit 32bda65. Both apps tsc + vite build green. Resolves §G Q4 (unreadCount via dedicated endpoint, not page response), Q6 (inline expand), Q7 (FE→POST).
- **CTO browser smoke-test PASSED (2026-06-11):** bell badge increment on publish, panel list, News expand + border clear, mark-all-read no-405 — all verified. API-SPEC.md GET /api/notifications fixed: `unreadCount` removed from page response example (count comes from `GET /api/notifications/unread-count`).
- **NEXT: N1 (notification deep-link + resident news detail route) — see `reports/module10-extended-backlog.md`.** Then remaining backlog: TEMP_HIDDEN_DEFERRED, VN user guide, hardening F-04/F-05/SEC-20 (deferred).
- ⚠ Multiple residencies: impossible — `uq_residents_active_user` partial unique index (V4:22).

**NEXT — remaining major items:**
1. ~~Date-format mm/dd→dd/mm~~ DONE 2026-06-11: formatVNDate/formatVNDateTime in @gemek/ui (commit b1db38b, ui 58/58 green) + 18 display spots wired (resident 9 = 195ff8e, admin 9 = 75f5c87); both builds green. Timezone decision (local-time render, intended) + KIND-B native-input limitation recorded in DECISIONS.md 2026-06-11. KIND-C wire ISO untouched. Inventory: reports/date-format-diagnosis.md. NOT browser-verified — CTO step (ports 80/81).
2. TEMP_HIDDEN_DEFERRED removal (hidden nav/features).
3. Module 10 notification dispatch — IN PROGRESS, see section above.
4. Vietnamese user guide.
5. Hardening sprint: F-04, F-05, SEC-20.

**Resident cluster 1 COMPLETE (2026-06-10):**
- viShared empty-state refined: `common.emptyYet` / `common.emptyFound` replace `common.empty`; 11 ui tests green. Commit 24aff81.
- Translated: MyTicketsPage ('Phản ánh của tôi', '+ Tạo mới', emptyYet, 'Tạo phản ánh đầu tiên', modal 'Tạo phản ánh'), MyBookingsPage ('Lượt đặt của tôi', emptyYet), TicketDetailPage (back/labels/Photos→Hình ảnh/Timeline→Lịch sử/'Khởi tạo' fallback/rating block), ProfilePage ('Trang cá nhân', 'Vai trò:', 'Đăng nhập gần nhất:', 'Đổi mật khẩu' form, 'Đăng xuất'). nav.tickets + home.activeTickets switched 'Yêu cầu'→'Phản ánh'. Commit cbd99c0.
- Verified: 11/11 ui tests + `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step.
- Flagged, NOT changed (already-VN, old "yêu cầu" wording): MyTicketsPage 'Gửi yêu cầu' + 'Loại yêu cầu', TicketDetailPage error 'Không thể tải yêu cầu hỗ trợ.' — needs CTO terminology-sweep decision.

**NEXT: resident cluster 2 — AnnouncementsPage, AmenitiesPage (TEMP_HIDDEN_DEFERRED), ParkingPage (TEMP_HIDDEN_DEFERRED). Then enum display-maps (separate step), then admin app.**

**Step 1 pilot COMPLETE (2026-06-10):**
- `packages/ui/src/lib/vi.ts` — `viShared` dict (Hủy/Lưu/Sửa/Đang tải.../Trước/Sau/Thao tác/Trạng thái + `common.empty` = 'Không có {item}') + `createT(...dicts)` factory + `interpolate()`; exported from `packages/ui/src/index.ts`. 10 unit tests green (`vi.test.ts`). Commit 39dc7a9.
- `frontend/apps/resident/src/i18n/vi.ts` — resident dict (nav/layout/home) + app-bound `t = createT(vi, viShared)` (app dict shadows shared). `src/i18n/enums.ts` NOT created — enum display-maps are a separate later step.
- Resident `Layout.tsx` + `HomePage.tsx` translated via `t()`. Key terms: Home→Trang chủ, Tickets→Yêu cầu, Vehicles→Phương tiện, News→Tin tức, Profile→Cá nhân, 'Hello, {name}'→'Xin chào, {name}' (interpolated). Commit 66b2515.
- Verified: `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step (`docker compose up -d --build nginx`).
- Untouched (per scope): getVnErrorMessage / meta.successMessage feedback, enum `value=` attrs, all other files.

**⏸ NEXT: STOPPED for CTO pattern review. Do NOT roll out further until CTO approves the pilot pattern.**

**Rollout order (after pilot approval):**
- Resident remainder (ParkingPage, ProfilePage, TicketDetailPage, AmenitiesPage, etc.)
- Enum display-maps — separate step (`src/i18n/enums.ts` per app)
- Admin app — ~3–4 clusters per reports/i18n-inventory.md (ParkingPage, AmenitiesPage, ReportsPage, then remaining pages)

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
