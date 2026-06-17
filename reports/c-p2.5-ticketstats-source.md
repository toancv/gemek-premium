# Backlog (c) — P2.5: Ticket-Stats Data-Source Diagnosis

**Phase:** P2.5 (CTO-approved scope addition 2026-06-17) — technician may see ticket STATISTICS via the Tickets page, NOT the business dashboard (dashboard stays `[ADMIN,BOARD_MEMBER]`, P2 Option 2). FRONTEND ONLY. This is STEP A (data-source diagnosis). No BE code.

- Branch `deploy/local`, HEAD `003448a`, tree clean (untracked scratch only).
- Stat semantics to mirror (from `reports/c-p2-route-audit.md` §3 / DashboardPage): open / in-progress, SLA-breached (overdue), by-category.

---

## §1 — Candidate sources (file:line evidence)

### (1) Dedicated ticket-stats endpoint?
- **`GET /api/tickets/sla-report`** — `TicketController.java:157-158`, `@PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")`. **Excludes TECHNICIAN.** SLA-focused.
- **`GET /api/reports/tickets`** — `ReportController.java:77-78`, `@PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")`. Returns per-category breakdown incl. `slaBreached` (`TicketServiceImpl.java:277-328`). **Excludes TECHNICIAN.**
- **No technician-admitted dedicated ticket-stats endpoint exists.**

### (2) Dashboard aggregate endpoint — LEAKS + gated
- **`GET /api/reports/dashboard`** — `ReportController.java:59-60`, `@PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")`. **Excludes TECHNICIAN.**
- Payload `DashboardResponse` bundles `apartments` + `tickets` + `amenities` + `contracts` (`DashboardPage.tsx:35-38` reads all four). **If a technician were admitted it would leak contract/occupancy data.** Unusable as-is on both counts (gated AND bundled).

### (3) Ticket LIST endpoint — TECHNICIAN-admitted, ticket-only, accurate via `total`
- **`GET /api/tickets`** — `TicketController.java:98-99`, `@PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')")`. **Admits TECHNICIAN.**
- Returns **`PageResponse<TicketSummaryResponse>`** — `PageResponse.java:41` exposes `total` (long = whole-dataset element count), plus `data` (page rows only), `totalPages`. **The whole-dataset count is `total`, independent of page size.**
- Filter params (`TicketController.java:102-106`): `status` (`List<TicketStatus>`), `category` (`TicketCategory`), `priority`, `apartmentId`, `visibility`. Filter spec: `buildFilterSpec` (`TicketServiceImpl.java:990-1009`) — status + category + priority + apartment predicates. **No overdue / SLA-deadline predicate exists.**
- **Role scoping** `buildScopeSpec` (`TicketServiceImpl.java:953-979`):
  - **TECHNICIAN** → `assignedToUser.id == me OR status == NEW` (their assigned work + the unassigned NEW claimable queue).
  - ADMIN / BOARD_MEMBER → no restriction (whole system).
  - RESIDENT → own household / community.
- **Consequence:** a count obtained as `GET /api/tickets?status=X&size=1 → total` is the accurate whole-dataset count **for that caller's authorized scope** — technician gets their scoped count, admin gets the system count. No client-side row counting (which would be WRONG: page size capped at 100 `TicketController.java:111`, dev DB holds ~249 tickets).
- `TicketStatus` enum (`TicketStatus.java:10-20`): `NEW, ASSIGNED, IN_PROGRESS, DONE, CANCELLED`.
- `TicketCategory` enum: `MAINTENANCE_REPAIR, COMPLAINT, ADMINISTRATIVE, SUGGESTION_FEEDBACK, OTHER`.

---

## §2 — Conclusion: (i) — technician-authorized, ticket-only source EXISTS

**Source = the ticket LIST endpoint + `PageResponse.total`, queried once per filter (status / category) with `size=1`.** Technician-admitted, ticket-only (no contracts/occupancy), role-scoped by the BE, and accurate (whole-dataset `total`, never page rows). Proceed to STEP B, FE-only. **No BE change.**

### Derivable accurately (technician-safe)
- Per-status counts: NEW, ASSIGNED, IN_PROGRESS, DONE (+ CANCELLED if wanted) → one `?status=X&size=1` query each, read `total`.
- "Open / active" = NEW + ASSIGNED + IN_PROGRESS (sum of three disjoint whole-dataset totals — exact).
- Per-category counts → one `?category=X&size=1` query each, read `total`.

### NOT derivable → OMITTED (never fabricated)
- **SLA-breached / overdue.** No overdue filter on the list (`buildFilterSpec` has none), and the dedicated SLA sources (`/api/tickets/sla-report`, `/api/reports/dashboard`, `/api/reports/tickets`) are all `[ADMIN,BOARD_MEMBER]`-gated. Surfacing an overdue count to a technician would need a **BE change** (add an `overdue` predicate to the list filter, OR widen an SLA endpoint's `@PreAuthorize`) — a separate gated decision, deferred. The stat block omits the SLA card rather than fabricate or page-count.

### STEP B plan (FE-only)
- `useTicketCount(filter)` hook → `GET /api/tickets` with `{...filter, page:0, size:1}`, returns `total`.
- TicketsPage top block: status count cards (NEW / ASSIGNED / IN_PROGRESS / DONE) mirroring dashboard StatCard styling + a "by-category" panel. SLA-breached omitted. Visible to all roles reaching the page (ticket data, harmless), no role-branching. Accuracy verified against a direct DB group-by in VERIFY.
