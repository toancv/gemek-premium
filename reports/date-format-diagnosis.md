# Date Format Diagnosis — mm/dd/yyyy → dd/mm/yyyy (FE, both apps)

**Date:** 2026-06-11 | **Scope:** frontend/apps/resident + frontend/apps/admin + packages/ui
**Status:** DIAGNOSIS ONLY — no code changed.

## Environment facts
- **No date library** in any package.json (no date-fns/dayjs/moment) — native `Date` only.
- **No shared date helper** — zero `formatDate`/date utils in `@gemek/ui`; every spot formats ad-hoc inline.
- Display bug root cause: `toLocaleDateString()` / `toLocaleString()` **without locale argument** → renders per browser locale (mm/dd/yyyy on en-US browsers). One spot already passes `'vi-VN'` (admin AnnouncementsPage:99).
- Second display bug class: **raw ISO wire strings rendered directly** (`{b.bookingDate}`) → user sees `yyyy-mm-dd`.

## KIND A — DISPLAY (the actual work): 18 spots

| # | App | File:line | Code | Current format | Proposed action |
|---|-----|-----------|------|----------------|-----------------|
| 1 | resident | pages/HomePage.tsx:54 | `new Date(a.publishedAt).toLocaleDateString()` | browser-locale (mm/dd) | `formatVNDate(a.publishedAt)` |
| 2 | resident | pages/AnnouncementsPage.tsx:37 | `new Date(a.publishedAt).toLocaleDateString()` | browser-locale | `formatVNDate` |
| 3 | resident | pages/MyTicketsPage.tsx:64 | `new Date(t.createdAt).toLocaleDateString()` | browser-locale | `formatVNDate` |
| 4 | resident | pages/TicketDetailPage.tsx:49 | `new Date(ticket.createdAt).toLocaleDateString()` | browser-locale | `formatVNDate` |
| 5 | resident | pages/TicketDetailPage.tsx:51 | `new Date(ticket.slaDeadline).toLocaleDateString()` | browser-locale | `formatVNDate` |
| 6 | resident | pages/TicketDetailPage.tsx:85 | `new Date(h.changedAt).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 7 | resident | pages/ProfilePage.tsx:50 | `new Date(me.lastLoginAt).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 8 | resident | pages/MyBookingsPage.tsx:44 | `{b.bookingDate}` rendered raw | yyyy-mm-dd (ISO) | `formatVNDate(b.bookingDate)` — display only, payloads elsewhere untouched |
| 9 | resident | pages/ParkingPage.tsx:50 | `{a.startDate}` rendered raw | yyyy-mm-dd | `formatVNDate` |
| 10 | admin | pages/AnnouncementsPage.tsx:99 | `toLocaleDateString('vi-VN')` | d/m/yyyy (already VN, unpadded) | switch to `formatVNDate` for padding + consistency |
| 11 | admin | pages/ParkingPage.tsx:174 | `new Date(g.entryTime).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 12 | admin | pages/ParkingPage.tsx:175 | `new Date(g.exitTime).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 13 | admin | pages/TicketDetailPage.tsx:139 | `new Date(ticket.slaDeadline).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 14 | admin | pages/TicketDetailPage.tsx:170 | `new Date(h.changedAt).toLocaleString()` | browser-locale datetime | `formatVNDateTime` |
| 15 | admin | pages/TicketsPage.tsx:125 | `new Date(t.slaDeadline).toLocaleDateString()` | browser-locale | `formatVNDate` |
| 16 | admin | pages/AmenitiesPage.tsx:126 | `{b.bookingDate}` rendered raw | yyyy-mm-dd | `formatVNDate` |
| 17 | admin | pages/ReportsPage.tsx:182 | `{c.endDate}` rendered raw | yyyy-mm-dd | `formatVNDate` |
| 18 | admin | pages/ResidentsPage.tsx:170 | `{r.moveInDate}` rendered raw | yyyy-mm-dd | `formatVNDate` |

Breakdown: 12 date-only, 6 datetime. 5 of the 18 are raw-ISO renders; 1 (admin Announcements) already vi-VN but unpadded.
NOT a date: admin ReportsPage.tsx:184 `contractValue?.toLocaleString('vi-VN')` — number formatting, leave alone.

## KIND B — INPUT (native `<input type="date">`): 6 spots — inventory only

Browser renders these per the **user's OS locale**; JS cannot change a native date input's display format. Flag, don't fix here.

| # | App | File:line | Field | Note |
|---|-----|-----------|-------|------|
| 1 | resident | pages/AmenitiesPage.tsx:66 | bookingDate | uncontrolled; `min=` ISO (see KIND C) |
| 2 | admin | pages/ParkingPage.tsx:210 | startDate | uncontrolled (FormData) |
| 3 | admin | pages/ReportsPage.tsx:36 | from | controlled; value = ISO state → query param |
| 4 | admin | pages/ReportsPage.tsx:40 | to | controlled; same |
| 5 | admin | pages/ResidentsPage.tsx:258 | dateOfBirth | controlled; value = ISO state → payload |
| 6 | admin | pages/ResidentsPage.tsx:289 | moveInDate | uncontrolled (FormData) |

Changing their displayed format would require replacing native inputs with masked text inputs or a picker lib — new dependency + UX/validation rework, NOT recommended in this task. VN-locale OS/browsers already show dd/mm/yyyy natively.

## KIND C — WIRE FORMAT: 5 spots — DO NOT TOUCH

| # | App | File:line | Code | Why untouchable |
|---|-----|-----------|------|-----------------|
| 1 | resident | pages/AmenitiesPage.tsx:66 | `min={new Date().toISOString().split('T')[0]}` | HTML date-input `min` attr REQUIRES ISO yyyy-mm-dd |
| 2 | admin | pages/ParkingPage.tsx:86 | `endDate: new Date().toISOString().split('T')[0]` | API payload — BE expects ISO |
| 3 | admin | pages/ReportsPage.tsx:36/40 | `value={from}` / `value={to}` ISO state | query params to BE + date-input `value` REQUIRES ISO |
| 4 | admin | pages/ResidentsPage.tsx:259 | `value={dateOfBirth}` ISO state | payload to BE + input value REQUIRES ISO |
| 5 | both | all `fd.get('…Date')` FormData reads | native date inputs emit ISO | payloads to BE |

(KIND C overlaps KIND B: every date input's value/min/max is ISO by HTML spec — that is correct and must stay.)

## Recommended fix strategy

1. **Add to `@gemek/ui`** (one new file `lib/dateFormat.ts` + test, exported from index):
   - `formatVNDate(iso: string | null | undefined): string` → `dd/mm/yyyy` (zero-padded, manual `getDate/getMonth/getFullYear` — NOT `toLocaleDateString('vi-VN')`, which gives unpadded d/m/yyyy and varies by engine); null/undefined/invalid → `''` (callers keep their own `'—'` fallbacks).
   - `formatVNDateTime(iso): string` → `dd/mm/yyyy HH:mm` (24h) for the 6 datetime spots.
2. **Replace the 18 KIND A spots** with the helpers (display only; expressions around them — fallbacks, breach flags — unchanged).
3. **KIND B**: leave native inputs as-is; note in PROGRESS as accepted limitation (OS-locale rendering). CTO may later opt for a picker lib — separate decision.
4. **KIND C**: zero changes.
5. Verify: ui tests (new dateFormat tests) + tsc + vite build both apps. Browser-verify = CTO (ports 80/81).

Effort: 1 ui commit (helper + tests) + 2 app commits (resident 9 spots, admin 9 spots) + docs(context).
