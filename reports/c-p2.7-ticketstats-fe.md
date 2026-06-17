# Backlog (c) — P2.7: SLA-breached card + stat-card drill-down (FRONTEND ONLY)

**Scope:** admin `TicketsPage` only. (1) add the SLA-breached stat card P2.5 omitted, now derivable via the P2.6 `?overdue=true` filter; (2) make every stat card a drill-down to the filtered list, for ALL roles. **No BE / `@PreAuthorize` / authStore / role-gate / routing-guard change.**

Branch `deploy/local`. Pre-change HEAD `d83d8e2`.

---

## §1 — Files changed
`frontend/apps/admin/src/pages/TicketsPage.tsx` (only). No hook/i18n change — reused `useTicketCount` (P2.5) and the existing VN term `t('dashboard.slaBreached')` = «Trễ hạn».

## §2 — PART 1: SLA-breached card
- New `SlaCountCard` sourced **the same way as the status cards**: `useTicketCount({ overdue: true })` → `GET /api/tickets?overdue=true&size=1` → `PageResponse.total`. Same counting mechanism, no new path.
- VN label: `t('dashboard.slaBreached')` = «Trễ hạn» — the exact term the dashboard/Reports already use (`vi.ts:38`, `reports.slaBreachedCol:213`). No coined term.
- Role-correct, no FE branching: server-scopes via `buildScopeSpec` — a technician's card shows THEIR overdue count, an admin's shows all.
- **No fabrication on failure:** `isError → '—'`, `isLoading → '…'`, else `data ?? 0`. The status cards' pre-existing `data ?? 0` (0-on-error) was NOT changed (out of P2.7 scope); the SLA card explicitly guards error per the task.
- Layout: status row regrid `grid-cols-4 → grid-cols-5` (NEW/ASSIGNED/IN_PROGRESS/DONE + SLA), red accent (`text-red-600`).

## §3 — PART 2: drill-down (all roles)
Every card/row clickable → navigates the filtered list on the SAME page via URL params.

| Card | Drill target URL | List query produced |
|------|------------------|---------------------|
| NEW | `/tickets?status=NEW` | `status=NEW` |
| ASSIGNED | `/tickets?status=ASSIGNED` | `status=ASSIGNED` |
| IN_PROGRESS | `/tickets?status=IN_PROGRESS` | `status=IN_PROGRESS` |
| DONE | `/tickets?status=DONE` | `status=DONE` |
| category card (each) | `/tickets?category=<CAT>` | `category=<CAT>` |
| **SLA-breached** | `/tickets?overdue=true` | `overdue=true` |

**Both directions wired:**
- (a) Click → `drillDown(patch)` sets the URL (`useSearchParams`).
- (b) `TicketsPage` SEEDS filter state FROM the URL on mount/param-change: `category/status/overdue/page` are read from `searchParams` (no local `useState` for them). Landing on `/tickets?overdue=true` or `?status=NEW` applies immediately.

## §4 — Source of truth + sync
**URL search params are the single source of truth.** `category`, `status`, `overdue`, `page` are derived from `searchParams` every render — no duplicate `useState`, so dropdowns, drill-downs, and the URL cannot diverge.
- In-page dropdowns (status/category) → `setFilter(patch)`: **merges** into existing params (preserves the others) and resets `page`. Manual refinement keeps working.
- Stat cards → `drillDown(patch)`: **replaces** all filters with the single one, so the resulting list length equals that card's own count (see §6).
- Pagination → `goPage(p)`: updates only `page`.
- `apartmentId` / `showCreate` / `formError` stay local `useState` (create-modal state, not list filters) — unchanged.

## §5 — overdue-with-no-control handling
The filter bar has only status + category dropdowns; `overdue` has no dropdown. Handling: when `overdue` is active (via a drill-down or a direct URL), the list query honors it **regardless of the visible controls**, and a **clearable red chip** «Trễ hạn ✕» renders in the filter bar → `setFilter({ overdue: '' })` clears it while preserving status/category. So the "overdue active but no UI control" state is visible and reversible, not silent/stuck.

## §6 — Static drill-down ⇄ count reasoning
Each card counts with exactly ONE unscoped filter (`useTicketCount({status})` / `({category})` / `({overdue:true})`). Because `drillDown` REPLACES all filters with that same single param, the list query is byte-identical to the count query (modulo paging). Therefore **list `total` == the card's number** for the same caller scope. Example: SLA card = `count(overdue=true)`; clicking → `/tickets?overdue=true` → list `total` = same value. Holds for every card.

## §7 — Verify
- `corepack pnpm --filter admin exec tsc --noEmit` → **exit 0**.
- `corepack pnpm --filter admin build` → **green** (587 modules, built 2.65s).
- **/code-review** (cavecrew-reviewer): 0 real bugs. 3 findings, all record-defer:
  1. `page` parse `|| 0` flagged "redundant" — **incorrect**: `Math.max(0, NaN)` is `NaN`, so `Number(...) || 0` is required to coerce NaN→0. Kept as-is (correct).
  2. `setFilter({overdue:'false'})` would round-trip-lose intent — **no live caller** passes `'false'` (clear uses `''`, drill uses `'true'`). Latent only → defer.
  3. mixed boolean/string in `params` — axios serializes `overdue:true`→`?overdue=true`; same pattern as existing `useTicketCount`/P2.5. Verified non-issue.

## §8 — CTO :80 smoke checklist (REQUIRED — current running container is the OLD image)
The overdue filter is BE code shipped in P2.6 but **not yet in the running container**. To smoke P2.7 end-to-end:
1. `docker compose up -d --build backend` (REBUILD, not restart — pulls the P2.6 `overdue` filter into the running jar).
2. `docker compose up -d --build nginx` (rebuild to serve the new admin bundle).
3. As **ADMIN on :80**, TicketsPage SLA card must read **459** (P2.6 DB ground truth for the current dev dataset — high due to known dev-DB test pollution, NOT a bug; see `reports/c-p2.6-overdue-filter.md` §7).
4. Click the SLA card → lands `/tickets?overdue=true`, list shows exactly that set (`total` == 459), red «Trễ hạn ✕» chip present and clearable.
5. Spot-check a status card (e.g. NEW) → `/tickets?status=NEW`, list `total` == the card number.

**P3 (admit TECHNICIAN) NOT started** — awaiting this smoke + the fixed P2 order.
