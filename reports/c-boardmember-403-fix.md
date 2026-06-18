# Backlog (c) — BOARD_MEMBER FE/BE 403 Fix (Direction A + announcements read-only)

**Date:** 2026-06-18
**Branch:** `deploy/local` (from HEAD `9ceb402`). **FRONTEND ONLY** — zero BE `@PreAuthorize` changes.
**CTO ruling applied:** Direction A for all 6 write mismatches (BOARD_MEMBER = read/oversight → HIDE write controls on FE, no BE permission change), plus #7 open `/announcements` to BOARD_MEMBER as READ-ONLY (route+nav) with all announcement writes hidden from BOARD.
**Basis:** `reports/c-boardmember-403-diagnosis.md` (table A = 6 write mismatches; section B = #7). Enforces the DECISIONS baseline (BOARD = read/oversight; writes ADMIN-only; BOARD denied staff-list + ticket notifications) on the FE.

---

## Role helper used

No shared role helper existed (only the ad-hoc `useAuthStore((s) => s.user?.role) === 'TECHNICIAN'` on TicketDetailPage). Added one small consistent helper:

- **`src/lib/useRoleFlags.ts`** → `useRoleFlags()` returns `{ role, isAdmin, isTechnician, isBoardMember }`, derived from `authStore.user.role`.

All gating below uses `isAdmin` / `isTechnician` from this helper. TicketDetailPage's prior inline `isTechnician` was migrated to it (removed the now-unused `useAuthStore` import there).

---

## Per-control table — gated to the BE's exact allowed set

| # | Control (VN) | File:line | Gated to | BE endpoint + allowed set | Match? |
|---|--------------|-----------|----------|---------------------------|--------|
| 1 | Phân công (ticket assign card) | `TicketDetailPage.tsx` assign card | `isAdmin` | `PUT /tickets/{id}/assign` = `hasRole('ADMIN')` | ✅ exact. Also removes the broken staff-picker (`GET /users` ADMIN-only) for BOARD. |
| 2 | Cập nhật trạng thái (ticket status card) | `TicketDetailPage.tsx` status card | `isAdmin \|\| isTechnician` | `PUT /tickets/{id}/status` = `hasAnyRole('ADMIN','TECHNICIAN')` | ✅ exact. TECHNICIAN keeps it; BOARD hidden. |
| 3 | + Thêm căn hộ (apartment create) | `ApartmentsPage.tsx` header button | `isAdmin` | `POST /apartments` = `hasRole('ADMIN')` | ✅ exact. |
| 4 | Sửa (apartment edit, per row) | `ApartmentsPage.tsx` row action | `isAdmin` | `PUT /apartments/{id}` = `hasRole('ADMIN')` | ✅ exact. |
| 5 | Thêm nhà thầu (contractor add) | `ContractorsPage.tsx` header button | `isAdmin` | `POST /contractors` = `hasRole('ADMIN')` | ✅ exact. |
| 6 | Sửa (contractor edit, per row) | `ContractorsPage.tsx` row action | `isAdmin` | `PUT /contractors/{id}` = `hasRole('ADMIN')` | ✅ exact. |
| 7a | `/announcements` route + nav | `App.tsx` route, `Layout.tsx` nav | `[ADMIN, BOARD_MEMBER]` | `GET /announcements` (+`/{id}`) admit `ADMIN,TECHNICIAN,BOARD_MEMBER,RESIDENT` | ✅ BOARD now reads (read-open). |
| 7b | Tạo thông báo (announcement create) | `AnnouncementsPage.tsx` header button | `isAdmin` | `POST /announcements` = `hasRole('ADMIN')` | ✅ exact. |
| 7b | Đăng (announcement publish, per row) | `AnnouncementsPage.tsx` row action | `isAdmin` (+ existing `!publishedAt`) | publish = `hasRole('ADMIN')` | ✅ exact. |

**Announcement write inventory (all gated to ADMIN):** create button + create modal trigger, per-row publish button + its confirm modal trigger. No edit/delete control is rendered on this page (BE update/delete exist but the admin UI never exposed them) → nothing else to gate. BOARD gets list + status + paging only.

---

## Correctness reasoning — no control hidden from a role that should see it

- **#1 assign → `isAdmin`**: BE is `hasRole('ADMIN')`. Previously `!isTechnician` (showed BOARD). Now ADMIN-only — matches BE. TECHNICIAN was already excluded; BOARD now excluded. No over-hide (only ADMIN qualifies on BE).
- **#2 status → `isAdmin || isTechnician`**: BE is `ADMIN,TECHNICIAN`. Critically **not** gated to ADMIN-only — TECHNICIAN keeps the card (their core work). BOARD removed. Exact match.
- **#3–#6 → `isAdmin`**: all four BE endpoints are `hasRole('ADMIN')`. Only ADMIN qualifies; hiding from BOARD (and any non-ADMIN) matches BE exactly. No role that the BE accepts is hidden.
- **#7a read-open**: BE GETs admit BOARD (and more); FE now lets BOARD reach the page → closes the inverse mismatch. RESIDENT/TECHNICIAN remain excluded by the route (`[ADMIN,BOARD_MEMBER]`), consistent with the admin portal not serving them.
- **#7b writes → `isAdmin`**: opening the route without this would create a NEW mismatch (BOARD clicks create/publish → 403). Both gated to ADMIN = BE set. BOARD sees read-only.

**Result:**
- BOARD_MEMBER: **no** write control on tickets / apartments / contractors / announcements; **can read** announcements (+ existing dashboard/apartments/contractors/reports reads). ✅
- ADMIN: every control unchanged (all gates include ADMIN). ✅
- TECHNICIAN: still has the ticket status card (#2); never had the others. ✅

---

## Verification

- `npx tsc --noEmit` → exit 0.
- `npx vite build` → ✓ 590 modules (was 588; +2 = `useRoleFlags` + helper graph), exit 0.
- No BE file touched. `homePathFor` / landing / P2 routing untouched (BOARD still lands `/dashboard`).
