# Backlog (c) — Reports Investigation: Cách 1 vs Cách 2 + SLA-breached source + drill-down

**Type:** READ-ONLY investigation. No code changed. Every claim cites `file:line`.
**Decision to inform:** should technicians enter the admin **Reports** page (Cách 1 — REVERSES ruling 3 "technician UI = TICKETS ONLY") to get FULL ticket stats incl. **SLA-breached** (which P2.5 had to OMIT), or keep stats on TicketsPage (Cách 2)? Plus: scope the drill-down (click a stat → filtered ticket list) for ALL roles.

- Branch `deploy/local`, HEAD `a990f8b`, tracked tree **clean** (untracked scratch + `scripts/GenHash.java` only).
- Ruling 3 (DECISIONS "Backlog (c) … 2026-06-17"): technician UI = TICKETS ONLY. **Cách 1 reverses this — flagged.**
- P2 (`reports/c-p2-route-audit.md`): `/reports` = `RequireRole [ADMIN,BOARD_MEMBER]`; technician landing `/tickets` via `homePathFor`.
- P2.5 (PROGRESS): TicketsPage stats from `GET /api/tickets` `PageResponse.total`; **SLA-breached omitted** — no list overdue filter + SLA endpoints ADMIN/BOARD-gated.

---

## §1 — Reports tab/section inventory

`ReportsPage.tsx:7` — 4 tabs: `summary | tickets | amenities | contracts`. Hooks `ReportsPage.tsx:11-14`.

| Tab | VN title | Shows | Endpoint (hook) | `@PreAuthorize` | Class |
|-----|----------|-------|-----------------|-----------------|-------|
| summary | `reports.tabSummary` (`:17`) | apartments total + **occupancyRate**, openTickets + **overdueRequests**, **active contracts** + expiring-30d, **amenity bookings/month** + pending, tickets-by-category bars (`:45-81`) | `GET /api/reports/dashboard` (`useDashboard`, hooks `:12`) | `hasAnyRole('ADMIN','BOARD_MEMBER')` (`ReportController.java:59-60`) | **BUSINESS (mixed)** |
| tickets | `reports.tabTickets` (`:18`) | total, completed, **slaBreachRate**, avgRating + period breakdown table incl. **slaBreached** col (`:84-125`) | `GET /api/reports/tickets` (`useTicketReport`, hooks `:266-267`) | `hasAnyRole('ADMIN','BOARD_MEMBER')` (`ReportController.java:77-78`) | TICKET |
| amenities | `reports.tabAmenities` (`:19`) | per-amenity bookings/approved/rejected/cancelled/utilization (`:127-159`) | `GET /api/reports/amenity-usage` (`useAmenityReport`, hooks `:269-270`) | `hasAnyRole('ADMIN','BOARD_MEMBER')` (`ReportController.java:98-99`) | **BUSINESS** |
| contracts | `reports.tabContracts` (`:20`) | contract title/contractor/endDate/daysToExpiry/**contractValue VND**/status (`:161-193`) | `GET /api/reports/contracts-expiring` (`useContractsExpiringReport`, hooks `:272-273`) | `hasAnyRole('ADMIN','BOARD_MEMBER')` (`ReportController.java:115-116`) | **BUSINESS** |

**Plain answer:** Reports is NOT ticket-only. **3 of 4 tabs carry business data a technician must NOT see** — `summary` (occupancy + contracts + amenity bookings), `amenities` (usage), `contracts` (contract values VND). Only `tickets` is ticket-only. Cách 1 must gate these 3 per-tab.

**Every Reports endpoint is `[ADMIN,BOARD_MEMBER]`-gated** — including both ticket-bearing ones (`/reports/dashboard`, `/reports/tickets`).

---

## §2 — SLA-breached source (the reason this investigation exists)

**Where SLA-breached lives:**
- `summary` tab: `dashboard.tickets.overdueRequests` (`ReportsPage.tsx:56`) → `GET /api/reports/dashboard`.
- `tickets` tab: `ticketReport.summary.slaBreachRate` (`:92`) + breakdown `b.slaBreached` (`:104,:114`) → `GET /api/reports/tickets`.
- Also `GET /api/tickets/sla-report` (`TicketController.java:157-158`), `hasAnyRole('ADMIN','BOARD_MEMBER')`.

**All three SLA-bearing endpoints are `[ADMIN,BOARD_MEMBER]`-gated.** None admits TECHNICIAN.

**Any TECHNICIAN-reachable overdue/SLA source?** The only ticket endpoint admitting TECHNICIAN is the list `GET /api/tickets` (`TicketController.java:98-99`) — and its filter spec `buildFilterSpec` (`TicketServiceImpl.java:990-1009`) has **no overdue / sla-deadline predicate** (only status/category/priority/apartment). So a technician **cannot** count overdue tickets from any current endpoint.

### ⭐ KEY FINDING — the decisive nuance
**Entering Reports does NOT give a technician SLA-breached.** The `summary` and `tickets` tabs' SLA widgets call `[ADMIN,BOARD_MEMBER]`-gated endpoints → a technician inside Reports gets **HTTP 403** on exactly those widgets. **A BE `@PreAuthorize` change (or a new technician-reachable overdue source) is required to give a technician SLA-breached REGARDLESS of Cách 1 vs Cách 2.** Cách 1's headline advantage ("Reports already has SLA") is illusory — the data is there, but the gate rejects the technician.

---

## §3 — «Phản ánh» (tickets) tab contents + reusability

**Fields shown:** summary cards `total` / `completed` / `slaBreachRate` / `avgRating` (`ReportsPage.tsx:88-95`); breakdown table per period: `label` / `total` / `completed` / `slaBreached` / `avgRating` (`:96-121`). Single endpoint: `GET /api/reports/tickets` (params `from`/`to`, `ReportsPage.tsx:12`).

**Reusable on TicketsPage (Cách 2 enrichment)?**
- **Hook** `useTicketReport` (hooks `:266-267`) is standalone — importable in isolation, only depends on `get('/reports/tickets', params)`. No coupling to the rest of ReportsPage.
- **Component:** the tab is **inline JSX inside ReportsPage**, NOT a standalone component — reusing the *visual* means extracting/duplicating markup (small).
- **BUT** the endpoint is `[ADMIN,BOARD_MEMBER]`-gated → calling `useTicketReport` from TicketsPage works for ADMIN/BOARD only; **a technician gets 403.** So the hook is reusable for staff stats, useless for technician SLA without a BE change (same §2 conclusion).

---

## §4 — Cách 1 cost (technician enters Reports)

**FE changes:**
- `/reports` route: `RequireRole [ADMIN,BOARD_MEMBER]` (`App.tsx`, per P2) → add `TECHNICIAN`.
- Per-tab role-gate inside ReportsPage: hide `summary`, `amenities`, `contracts` tabs from technician; show only `tickets`. The `tabs` array (`ReportsPage.tsx:16-21`) must filter by role; default `tab` state (`:7`, `'summary'`) must become `'tickets'` for technician (else lands on a hidden/forbidden tab).
- Nav (`Layout.tsx`): add Reports nav for technician (currently `[ADMIN,BOARD_MEMBER]`).
- P2 revision: `homePathFor` + access matrix must add `/reports` to technician's reachable set — contradicts P2's "tickets-only reachable" matrix; the whole P2 audit narrative changes.

**Leak risk per business tab (FE-hide vs endpoint):** FE-hiding the 3 business tabs is **NOT sufficient** — the endpoints (`/reports/dashboard`, `/amenity-usage`, `/contracts-expiring`) currently allow `[ADMIN,BOARD_MEMBER]`; once `/reports` admits technician at the route level, the *route* is reachable but those endpoints still reject technician at the BE (good — defense holds). However the `tickets`-tab endpoints (`/reports/dashboard` for summary AND `/reports/tickets`) **also reject technician** — so the one tab Cách 1 wants to expose **doesn't work for technician without widening `/reports/tickets`'s `@PreAuthorize`**. Net: Cách 1 needs a BE change anyway (§2).

**DECISIONS edit:** reversing ruling 3 ("TICKETS ONLY") must be explicitly re-ruled + recorded.

**Overlap with P2.5:** if Cách 1 chosen, the P2.5 TicketsPage stat block is partly redundant for technician (they'd see stats in Reports). Options: keep P2.5 (TicketsPage stats are still useful inline, no harm), remove it (loses the quick on-page view), or keep-for-all. **No strong reason to remove** — P2.5 is harmless and serves all roles on the page they already use. (Analysis only — no change.)

---

## §5 — Drill-down (both paths, all roles)

**Does TicketsPage read URL query params today?** **No.** It holds filter state in local `useState` only — `category`, `status`, `page`, `apartmentId` (`TicketsPage.tsx:22-26`); imports `useNavigate` but **no `useSearchParams`**. It cannot consume `/tickets?status=NEW` on mount today — needs a `useSearchParams`-seeded initial state (small FE change).

**Stat → exact `GET /api/tickets` filter param:**

| Stat | Param + value | Supported? |
|------|---------------|------------|
| NEW count | `status=NEW` | ✅ (`TicketController.java:102`) |
| ASSIGNED count | `status=ASSIGNED` | ✅ |
| IN_PROGRESS count | `status=IN_PROGRESS` | ✅ |
| DONE count | `status=DONE` | ✅ |
| by-category (each) | `category=<CAT>` | ✅ (`:104`) |
| **SLA-breached / overdue** | — none — | ❌ **no param** — `buildFilterSpec` (`TicketServiceImpl.java:990-1009`) has no overdue predicate; needs a new BE filter (e.g. `overdue=true` → `sla_deadline < now AND status NOT IN (DONE,CANCELLED)`) |

So every P2.5 stat drills down via an existing param **except SLA-breached**, which has no equivalent — the same BE gap as §2.

---

## §6 — Recommendation

| | **Cách 1 — technician enters Reports** | **Cách 2 — stats stay on TicketsPage (P2.5)** |
|---|---|---|
| Ruling 3 | **REVERSED** (re-rule + DECISIONS edit) | preserved |
| FE cost | `/reports` gate + per-tab role filter + default-tab fix + nav + **P2 matrix/homePathFor rewrite** | none beyond P2.5 (optionally `useSearchParams` for drill-down) |
| Business-leak surface | opens a 3-business-tab page to technician; relies on per-tab hide + BE endpoint gates holding | zero — technician never near business data |
| **SLA-breached for technician** | **STILL 403** — needs BE `@PreAuthorize` widen on `/reports/tickets` (or `/reports/dashboard`) | needs BE change too — add `overdue` filter to `/api/tickets` (technician already authorized) |
| BE change needed for technician SLA? | **YES** | **YES** |
| Drill-down enablement | Reports has no row→list links today; would still need TicketsPage URL-param work | add `overdue` filter doubles as the SLA drill-down param |

**The SLA-breached BE-change question (explicit):** a BE change is required in **BOTH** paths — there is no technician-reachable overdue/SLA source today. **Cách 1's main advantage therefore disappears:** entering Reports does not deliver SLA to a technician without also widening a `@PreAuthorize`.

**Recommendation (evidence one-sided → Cách 2):** Keep ruling 3 (Cách 2). Since a BE change is unavoidable for technician SLA, make it the **minimal, cleanest one**: add an `overdue` boolean filter to the ticket LIST endpoint (`/api/tickets`, which already admits TECHNICIAN and is role-scoped) → predicate `sla_deadline < now AND status NOT IN (DONE,CANCELLED)`. This single change (a) lets P2.5 add the omitted SLA-breached card via `useTicketCount({overdue:true})`, (b) doubles as the SLA drill-down param, and (c) requires **no** ruling reversal, **no** business-tab exposure, **no** P2 matrix rewrite. Cách 1 costs strictly more (reverse ruling 3 + gate 3 business tabs + rewrite P2 matrix) **and still needs a BE change**. Recommend Cách 2 + the `overdue` list-filter BE change as a separate gated P-step.

> Note: any BE change (widening `@PreAuthorize` OR adding the `overdue` filter) is a **gated decision** — flag to CTO, do not implement in an FE phase.
