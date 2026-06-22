# Backlog (c) — BOARD_MEMBER FE/BE 403 Mismatch Diagnosis (READ-ONLY)

**Date:** 2026-06-18
**Branch:** `deploy/local`, HEAD `2ef93fe`. Tree: no modified tracked files (untracked `reports/` scratch + `scripts/GenHash.java` only — pre-existing).
**Scope:** READ-ONLY diagnosis. No code changed, no fix applied. The fix direction per mismatch is a CTO product ruling (see A/B columns).
**Trigger:** P2 STEP B flagged a pre-existing BOARD_MEMBER FE/BE mismatch on the Tickets surface (`reports/c-p2-stepB-applied.md` §"In-page admin-only control audit", lines 92–97). This pass does NOT stop at that one — it sweeps every admin route BOARD_MEMBER is admitted to.

---

## Method

1. Confirmed the BOARD_MEMBER-admitted FE routes from **current** `App.tsx` (ground truth, not the report):
   `/dashboard`, `/apartments`, `/tickets`, `/tickets/:id`, `/contractors`, `/reports`, `/profile`.
   ( `[ADMIN]`-only and therefore hidden from BOARD: `/residents`, `/users`, `/announcements`, `/vehicles`. )
2. For each admitted page, inventoried every action/control BOARD_MEMBER can see/click.
3. Matched each control's endpoint to its BE `@PreAuthorize`.
4. Flagged every FE-permissive → BE-403 case, plus the inverse (BE-admits → FE-hides).

> **Correction vs the original flag.** `reports/c-p2-stepB-applied.md` listed **three** ticket mismatches for BOARD (create / assign / status). The **create-ticket button has since been removed from the admin app for ALL roles** (`TicketsPage.tsx:166-168` — "No create-ticket affordance in the admin app… Removed for all roles"). So create is **no longer a mismatch**. Two ticket mismatches remain, and four more exist on Apartments/Contractors that the ticket-scoped flag never covered.

---

## BOARD_MEMBER design baseline (for the CTO's per-mismatch ruling)

From `DECISIONS.md` + code ground truth — what BOARD_MEMBER is **intended** to be:

- **Admin-portal member, oversight/read tier.** Admin portal admits `['ADMIN','BOARD_MEMBER']` (`DECISIONS.md:665`).
- **Reports = `[ADMIN,BOARD_MEMBER]`** by design (read) — confirmed in `ReportController` (all 5 endpoints A+B).
- **Read access** to apartments list, contractors list, tickets list, contracts-expiring, blocks list — all BE endpoints already admit BOARD.
- **NOT a staff manager:** "BOARD_MEMBER does NOT get staff-list read", UsersPage stays ADMIN-only (`DECISIONS.md:697`).
- **NOT a ticket notification recipient:** TICKET_CREATED + SLA escalations go to ADMINs only, no BOARD (`DECISIONS.md:595`).
- **Operational read** on amenity bookings was deliberately kept for BOARD+TECHNICIAN (`DECISIONS.md:416-419`).

**Pattern:** every BOARD-admitted endpoint today is a **GET (read)**. Every **write** (apartment/contractor CRUD, ticket assign/status, user mgmt) is `hasRole('ADMIN')` or an ADMIN/TECHNICIAN set that excludes BOARD. The mismatches below are all cases where the FE renders a **write** control on a page BOARD can read, but the BE write endpoint excludes BOARD.

---

## A) FE-permissive → guaranteed BE 403 on click (6 mismatches)

Legend — **Direction A** = FE too permissive → hide/guard the control on FE (safe, no permission change).
**Direction B** = BE too strict by design → add `BOARD_MEMBER` to the BE `@PreAuthorize` (⚠ **PRIVILEGE GRANT** — security-sensitive, needs explicit CTO approval + a BE phase with tests).

| # | Action (VN) | FE control (file:line) | BE endpoint + `@PreAuthorize` (file:line) | BOARD result | A: hide on FE | B: grant on BE (⚠) |
|---|-------------|------------------------|-------------------------------------------|--------------|---------------|--------------------|
| 1 | **Phân công yêu cầu** (assign a ticket to staff/contractor) | `TicketDetailPage.tsx:186` — assign card rendered when `!isTechnician` (so BOARD sees it); submit `handleAssign`→`useAssignTicket` `PUT /tickets/{id}/assign` (`hooks.ts:143`) | `TicketController.java:212-213` `PUT /{id}/assign` → `hasRole('ADMIN')` | **403** on submit. Also the staff picker (`loadStaffOptions`, `TicketDetailPage.tsx:48-51`) calls `GET /users` which is `hasRole('ADMIN')` (`UserController.java:78-79`) → **403 while loading the dropdown too**. | Gate the assign card to `ADMIN` (extend the existing `!isTechnician` to an `isAdmin` check). | Add `BOARD_MEMBER` to `/{id}/assign` **and** to `GET /users` — grants BOARD ticket-assignment + staff-list read (the latter directly contradicts `DECISIONS.md:697`). |
| 2 | **Cập nhật trạng thái** (change ticket status) | `TicketDetailPage.tsx:228-245` — status card always rendered (no role gate); submit `handleStatusUpdate`→`useUpdateTicketStatus` `PUT /tickets/{id}/status` (`hooks.ts:152`) | `TicketController.java:236-237` `PUT /{id}/status` → `hasAnyRole('ADMIN','TECHNICIAN')` | **403** on submit (BOARD not in set). | Gate the status card to `[ADMIN,TECHNICIAN]`. | Add `BOARD_MEMBER` to `/{id}/status` — grants BOARD ticket-workflow mutation. |
| 3 | **Thêm căn hộ** (create apartment) | `ApartmentsPage.tsx:50-53` — "+ Thêm căn hộ" button, no role gate; submit `useCreateApartment` `POST /apartments` (`hooks.ts:53`) | `ApartmentController.java:99-100` `POST` → `hasRole('ADMIN')` | **403** on submit. | Gate the create button to `ADMIN`. | Add `BOARD_MEMBER` to `POST /apartments` — grants BOARD apartment creation. |
| 4 | **Sửa căn hộ** (edit apartment) | `ApartmentsPage.tsx:102` — "Sửa" link per row, no role gate; submit `useUpdateApartment` `PUT /apartments/{id}` (`hooks.ts:62`) | `ApartmentController.java:137-138` `PUT /{id}` → `hasRole('ADMIN')` | **403** on submit. | Gate the edit control to `ADMIN`. | Add `BOARD_MEMBER` to `PUT /apartments/{id}` — grants BOARD apartment edit. |
| 5 | **Thêm nhà thầu** (create contractor) | `ContractorsPage.tsx:36` — add button, no role gate; submit `useCreateContractor` `POST /contractors` (`hooks.ts:165`) | `ContractorController.java:101-102` `POST` → `hasRole('ADMIN')` | **403** on submit. | Gate the add button to `ADMIN`. | Add `BOARD_MEMBER` to `POST /contractors` — grants BOARD contractor creation. |
| 6 | **Sửa nhà thầu** (edit contractor) | `ContractorsPage.tsx:66` — "Sửa" link per row, no role gate; submit `useUpdateContractor` `PUT /contractors/{id}` (`hooks.ts:174`) | `ContractorController.java:132-133` `PUT /{id}` → `hasRole('ADMIN')` | **403** on submit. | Gate the edit control to `ADMIN`. | Add `BOARD_MEMBER` to `PUT /contractors/{id}` — grants BOARD contractor edit. |

**Note on #1's compound symptom:** even before submit, the assign card's SearchableSelect cannot populate for BOARD because `GET /users` is ADMIN-only — so BOARD sees an assign form that can neither load staff options nor submit. Both 403s collapse if the card is gated to ADMIN (Direction A); Direction B would require granting BOARD *two* ADMIN-only surfaces.

### NOT mismatches (verified clean)

- **TicketsPage** create-ticket button: removed for all roles (`TicketsPage.tsx:166-168`). Stat cards / filters / drill-down → `GET /tickets` (admits B). ✅
- **ReportsPage** (all 4 tabs) + **DashboardPage**: read-only; every call is `GET /reports/*` → `[ADMIN,BOARD_MEMBER]` (`ReportController.java:60,78,99,116,130`). No write controls, no export. ✅
- **ProfilePage**: `/auth/me`, `/auth/me/profile`, `/auth/me/password` (`AuthController.java:174,187,207`) have **no `@PreAuthorize`** → authenticated-only, BOARD admitted. ✅
- **No FE delete controls** for apartments/contractors (BE `DELETE` endpoints exist but are unreachable from the BOARD UI). ✅

---

## B) Inverse — BE admits BOARD_MEMBER but FE hides it (lower priority, 1 case)

| Action (VN) | BE endpoint admits BOARD | FE state | Note |
|-------------|--------------------------|----------|------|
| **Xem thông báo** (read announcements list + detail) | `AnnouncementController.java:81` `GET` list and `:135` `GET /{id}` → `hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER','RESIDENT')` | FE `/announcements` route is `RequireRole [ADMIN]` (`App.tsx:73`) + nav `[ADMIN]` (`Layout.tsx:26`) → BOARD never reaches the page | BE intends BOARD can read announcements; FE hides the whole page. Read-only, no 403 risk (nothing to click) — purely a hidden capability. Announcement **writes** (`:111/:161/:185/:209`) are ADMIN-only, consistent with hiding. CTO ruling: either expose `/announcements` (read) to BOARD, or tighten the BE GETs to drop BOARD. |

No other inverse cases: `/residents` and `/vehicles` BE endpoints admit only ADMIN/RESIDENT (no BOARD), and `/users` is ADMIN-only by explicit decision — all consistent with the FE `[ADMIN]` guard.

---

## Why no recommendation per mismatch

Directions A vs B hinge on **whether BOARD_MEMBER is meant to be a read-only oversight role or an operational co-admin** — a product decision, not a code fact. The baseline above (every BOARD endpoint today is a read; writes are ADMIN-only; BOARD is explicitly denied staff-list and ticket notifications) leans oversight-only, which would point to Direction A across the board — but the CTO owns that call. Each Direction B is a **privilege grant** and must go through an explicit approval + BE phase with `@PreAuthorize` tests; do not bundle B silently into an FE fix.
