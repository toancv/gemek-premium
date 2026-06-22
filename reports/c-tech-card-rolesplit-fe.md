# Backlog (c) FE — Role-aware ticket stat cards (technician «Trễ hạn của tôi» + hide mine card)

**Scope:** FRONTEND ONLY. Per CTO product ruling on `reports/c-tech-overdue-card-diagnosis.md` (verdict: scope-correct, NOT a bug — a label/semantics fix). No BE / `@PreAuthorize` / authStore / route-guard change. Both `overdue` (P2.6) and `mine` (P2.8, server-derived) filters already exist and compose (BE ANDs them).

Branch `deploy/local`. Pre-change HEAD `188e577`, tree had only untracked `reports/*` + `scripts/GenHash.java` (no tracked mods).

---

## §1 — What changed (production)

| File | Change |
|------|--------|
| `frontend/apps/admin/src/i18n/vi.ts` | + `dashboard.slaBreachedMine = 'Trễ hạn của tôi'`. |
| `frontend/apps/admin/src/pages/TicketsPage.tsx` | `SlaCountCard` made role-aware (`mine`+`title` props); `TicketStats` takes `isTechnician` (grid `5` vs `6`, conditional source/label/drill, mine card hidden for tech); page reads `isTechnician` from `authStore`; chip block role-aware (single combined chip for tech). |

`useAuthStore`, the role source, is the **same** store the nav role-gate (`Layout.tsx:45`) reads. Role read = `useAuthStore((s) => s.user?.role) === 'TECHNICIAN'` — primitive string, zustand `Object.is`-stable, no extra re-render.

## §2 — TECHNICIAN path (the only behavior that differs)

- **«Trễ hạn của tôi» count source** — `SlaCountCard mine={true}` → `useTicketCount({ overdue: true, mine: true })` → `GET /api/tickets?overdue=true&mine=true` reading `PageResponse.total`. P2.8 proved `overdue ∧ mine` ANDs server-side → the tech's OWN overdue count (bucket (i) in the diagnosis = **1** for the tested tech), NOT the 327:1 shared-NEW-queue-inflated 328.
- **Drill-down** — card `onClick={() => onDrill({ overdue: 'true', mine: 'true' })}`. `drillDown` REPLACES all URL filters with exactly these two → `/tickets?overdue=true&mine=true`. Card count and drilled list use the **same** filter set.
- **List length == card** — list query builds `params` with `...(overdue && {overdue:true}), ...(mine && {mine:true})` → `GET /api/tickets?overdue=true&mine=true` → identical predicate/scope as the count → `list.total == card`. ✔
- **Mine card absent** — `{!isTechnician && <MineCountCard .../>}` → not rendered for technician. Grid is `grid-cols-5` (4 status + SLA) so no empty slot.
- **Single combined chip** — technician branch renders ONE red «Trễ hạn của tôi» ✕ chip, guarded `(overdue || mine)`, `onClick={() => setFilter({ overdue: '', mine: '' })}` → clears BOTH keys at once. No two-chip desync; no orphaned-`mine` stuck filter (the `|| mine` guard catches a direct-URL `?mine=true`).

## §3 — ADMIN / BOARD_MEMBER path (unchanged — verified byte-for-byte)

- `SlaCountCard mine={false}` → `useTicketCount({ overdue: true })` (building-wide), title `t('dashboard.slaBreached')` = «Trễ hạn», drill `onDrill({ overdue: 'true' })` → `/tickets?overdue=true`. Identical to pre-change.
- `<MineCountCard onClick={() => onDrill({ mine: 'true' })} />` rendered (mine card visible).
- Grid `grid-cols-6` (4 status + SLA + mine).
- Chip block (non-tech branch) = the prior two independent chips: red «Trễ hạn» ✕ clears `overdue` only, indigo «Phân công cho tôi» ✕ clears `mine` only (each via `setFilter`, preserving the other).

## §4 — Untouched (all roles)

Status cards (NEW/ASSIGNED/IN_PROGRESS/DONE), by-category rows, `drillDown`/`setFilter`/`goPage`, list table/pagination — unchanged. Diagnosis confirmed status/category cards are scope-correct (technician sees scoped counts by design); no relabel/hide. No misleading card noted.

## §5 — Verification

- **admin `tsc --noEmit` exit 0**; **vite build green** (588 modules, 434.49 kB).
- **/code-review** (cavecrew-reviewer on the diff): 1🔴 addressed — technician chip now guards `(overdue || mine)` and clears both, so a direct-URL orphan `?mine=true` is reversible (no silent stuck filter). Remaining flags were non-issues: grid-cols-5 (MineCountCard is `!isTechnician`-gated, cannot render for tech); `slaBreachedMine` is used (tech card + chip); zustand primitive selector is stable.
- **NOT browser-verified — CTO :80 smoke.** The running nginx serves the OLD FE bundle; rebuild required (`docker compose up -d --build nginx`). Expectation: technician «Trễ hạn của tôi» ≈ their own overdue count (≈1 for the tested tech, not 328), no «Phân công cho tôi» card; ADMIN unchanged (building-wide «Trễ hạn», «Phân công cho tôi» present).

## §6 — Static trace summary

| Check | TECHNICIAN | ADMIN/BOARD |
|-------|-----------|-------------|
| Overdue card label | «Trễ hạn của tôi» | «Trễ hạn» |
| Overdue card source | `overdue=true & mine=true` | `overdue=true` |
| Overdue drill-down | `?overdue=true&mine=true` | `?overdue=true` |
| card count == drilled list | ✔ (same filter set) | ✔ (unchanged) |
| «Phân công cho tôi» card | hidden | visible |
| Chip on overdue active | single «Trễ hạn của tôi» ✕ → clears overdue+mine | «Trễ hạn» ✕ → clears overdue only |
| Grid | `grid-cols-5` | `grid-cols-6` |

This closes the technician stat-card semantics follow-up from `reports/c-tech-overdue-card-diagnosis.md`.
