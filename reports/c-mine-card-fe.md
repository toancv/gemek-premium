# Backlog (c) FE — "Phân công cho tôi" assigned-to-me stat card (FRONTEND ONLY)

**Scope:** add an assigned-to-me stat card to the admin TicketsPage stat block, sourced via the P2.8 `?mine=true` filter, with click-drill-down + clearable chip, for ALL admin roles. **No BE / @PreAuthorize / authStore / route-guard change.** Mirrors the P2.5/P2.7 card pattern exactly.

Branch `deploy/local`. Pre-change HEAD `7e45433` (P2.8 BE landed). One page + one i18n key.

---

## §1 — What changed (production)

| File | Change |
|------|--------|
| `frontend/apps/admin/src/i18n/vi.ts` | + `dashboard.assignedToMe = 'Phân công cho tôi'` (CTO-confirmed wording — assigned-to-me, NOT "Phản ánh của tôi"). |
| `frontend/apps/admin/src/pages/TicketsPage.tsx` | + `MineCountCard` component; card added to the stat grid (`grid-cols-5`→`grid-cols-6`); `mine` URL-state + list-param threading; clearable «Phân công cho tôi ✕» chip. |

No new counting mechanism, no new hook, no role-branching.

## §2 — Card source (same mechanism as every other card)

`MineCountCard` calls `useTicketCount({ mine: true })` → `GET /api/tickets?mine=true&page=0&size=1` reading `PageResponse.total` — identical to how the P2.5 status cards and the P2.7 SLA card source their counts. `mine` is **server-derived** (BE resolves it against the caller's principal id; the FE sends NO user id), so:
- ADMIN → count of tickets assigned to that admin.
- TECHNICIAN → count of tickets assigned to that technician.
- **No FE role-branching** — one card, correct per caller automatically.

No fabrication on failure: `isError → '—'`, `isLoading → '…'`, else `data ?? 0` (mirrors `SlaCountCard`).

## §3 — Drill-down

Card `onClick={() => onDrill({ mine: 'true' })}` → `drillDown` REPLACES all URL filters with the single `mine=true` → `/tickets?mine=true`. The existing `useSearchParams` seeding (single source of truth) reads `mine = searchParams.get('mine') === 'true'` and the list query includes `...(mine && { mine: true })`, so the list filters to the caller's assigned tickets on landing. Because the card count and the drilled list both come from `GET /api/tickets?mine=true` over the same caller scope, **the resulting list length equals the card count** for that caller (the list's `total` == the card number).

## §4 — Clearable chip (no dropdown, mirrors overdue)

`mine` has no in-page dropdown (filter controls are status/category only) — same as `overdue`. When `mine` is active (drill-down or direct URL) the list query honors it regardless of visible controls, and a clearable chip «Phân công cho tôi ✕» renders in the filter bar (indigo, mirroring the red «Trễ hạn ✕» chip). Clicking it calls `setFilter({ mine: '' })`, which deletes only the `mine` key and preserves the others (and resets page). Active filter is visible + reversible, never silent.

## §5 — mine × overdue coexistence (verified)

Both `mine` and `overdue` are URL-driven non-dropdown filters. The list query builds `params` with **both** spread independently:
```
{ page, size, ...(category && {category}), ...(status && {status}), ...(overdue && {overdue:true}), ...(mine && {mine:true}) }
```
So `?mine=true&overdue=true` honors BOTH (the BE ANDs them — proven by P2.8 `mineTrueOverdueTrue` test). Both chips render independently (each guarded by its own flag) and each clears only its own key via `setFilter`, which merges/preserves the rest. The only path that REPLACES all filters is stat-card `drillDown` (intended: a card's count must equal its single-filter list length). So the two filters never fight:
- click SLA card → `?overdue=true`; click mine card → `?mine=true` (each a clean single-filter drill).
- land on `/tickets?mine=true&overdue=true` (or combine via chips left after manual URL) → both chips show, both clearable independently, list shows the caller's own overdue-open tickets.

## §6 — Static trace summary

- **Card → list:** `useTicketCount({mine:true})` and `useTickets({...,mine:true})` both hit `GET /api/tickets?mine=true` → same caller scope, same predicate → card `total` == list `total` == list rows for that caller. ✔
- **mine+overdue:** both spread into list params, BE ANDs → caller's overdue-open subset. ✔
- **chips independent:** `setFilter({mine:''})` deletes `mine` only; `setFilter({overdue:''})` deletes `overdue` only; the other key + value survive (URLSearchParams copy-then-delete). ✔
- **All existing cards + drill-down + overdue chip left intact** (status ×4, SLA ×1, by-category rows; `drillDown`/`setFilter`/`goPage` unchanged). ✔

## §7 — Verification

- **admin `tsc --noEmit` exit 0**; **vite build green (588 modules, 433.93 kB)**. No unused imports added (`useTicketCount`, `t`, `StatCard` all pre-existing).
- **/code-review:** see §8.
- **NOT browser-verified — CTO :80 smoke** (rebuild backend+nginx; the running container is the OLD image without the P2.8 BE filter, so the card reads 0/whole-set until redeploy).

## §8 — /code-review

cavecrew-reviewer on the working-tree diff: **clean, 0 correctness bugs.** Verified: `mine` URL parse mirrors `overdue`; mine+overdue coexist in list params and never clobber (each chip clears only its own key via `setFilter` merge); `drillDown` REPLACES (card count == single-filter list); `grid-cols-6` valid (Tailwind JIT picks up the literal class — vite build confirms); no unused imports; `'—'`-on-error matches `SlaCountCard`.

## §9 — Smoke checklist (CTO, port 80)

After `docker compose up -d --build backend nginx`:
1. As the admin account P2.8 tested → TicketsPage «Phân công cho tôi» card reads **23** (P2.8 dev-DB ground truth for THAT user). A technician sees THEIR own assigned count, not 23.
2. Click the card → `/tickets?mine=true`, list shows exactly that set, list `total` == 23, indigo «Phân công cho tôi ✕» chip visible.
3. Chip clears it (`setFilter({mine:''})`) → filter removed, full scoped list returns.
4. Combine: from `?mine=true`, append `&overdue=true` (or via drill then manual) → both chips show, list = caller's own overdue-open; clearing one leaves the other active.
5. All P2.5/2.7 cards + «Trễ hạn ✕» chip still work unchanged.

**This completes the (c) ticket-stats follow-ups** (admin create-ticket removal done; assigned-to-me card done).
