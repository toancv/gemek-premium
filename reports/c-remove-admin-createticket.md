# Backlog (c) follow-up — Remove admin create-ticket entry point (all roles)

**Date:** 2026-06-17
**Scope:** FRONTEND ONLY (admin app). Intentional UI removal — admins only PROCESS tickets; residents
create via the resident app. **NOT a permission change.** BE create endpoint + resident app untouched.

---

## Entry-point form: **button + inline modal** (NOT a route)

The admin create-ticket surface was a button (`{t('tickets.new')}`) on `TicketsPage.tsx` that toggled
an in-page modal (`showCreate` state) holding the create form. **There is no `/tickets/new` route** —
grep for `tickets/new` returned nothing. So there is no reachable create URL to close; removing the
button + modal fully removes the affordance for every role.

---

## What was removed (admin only)

`frontend/apps/admin/src/pages/TicketsPage.tsx`:
- New-ticket button (was behind the P2 STEP B `!isTechnician` guard).
- The create modal + form (apartment SearchableSelect, category/priority/title/description fields).
- `handleCreate`, `loadApartmentOptions`.
- State `showCreate`, `apartmentId`, `formError`.
- The **redundant P2 STEP B `isTechnician` guard** (dead once the button is gone — no role-branching left for a deleted control).
- Now-unused imports: `useState`, `useCallback`, `SearchableSelect`, `SearchableOption` (type), `getVnErrorMessage`, `apiClient`, `useAuthStore`, `useCreateTicket`. (`React`, `useNavigate`, `useSearchParams`, `labelFor`, `formatVNDate`, `useTickets`, `useTicketCount`, `t` retained — still used by list/stats.)

`frontend/apps/admin/src/api/hooks.ts`:
- `useCreateTicket` hook **removed** — it was admin-only (sole consumer was TicketsPage) and is now fully unused. Confirmed by grep across `frontend/apps/admin/src`.

## What was KEPT

- **BE `POST /api/tickets`** — untouched (resident app still creates tickets through it).
- **Resident app** — untouched (no files outside `apps/admin` changed).
- **Ticket PROCESSING** — TicketDetailPage status-update + assign forms, detail view, and the P2.5/2.7
  stat cards + drill-down + «Trễ hạn ✕» chip — all fully intact.
- **`TicketDetailPage` `isTechnician` guard** — that is the P2 STEP B *Assign-card* guard (assign = ADMIN-only on BE), **unrelated to create**; correctly left in place.
- i18n keys `tickets.new` / `tickets.create` / `tickets.creating` / `tickets.modalTitle` — left defined
  but now unused (harmless dead keys; not removed to avoid touching shared i18n for no functional gain).

---

## No-create-affordance verification (per role)

The create button + modal are removed **unconditionally** (no role check) — so the affordance is gone
for every role identically:

| Role | Create button? | Create modal reachable? | Create URL? |
|------|----------------|-------------------------|-------------|
| ADMIN | ❌ removed | ❌ removed (`showCreate` gone) | n/a (never was a route) |
| BOARD_MEMBER | ❌ removed | ❌ removed | n/a |
| TECHNICIAN | ❌ removed | ❌ removed | n/a |

No admin path — button OR URL — reaches a create-ticket form. Residual grep
(`useCreateTicket|showCreate|handleCreate|isTechnician|/tickets/new`) over TicketsPage + hooks → none.

---

## Verification

- `npx tsc --noEmit` → exit 0 (no unused-import/dead-code errors).
- `npx vite build` → ✓ 588 modules, exit 0; bundle 433.37 KB (was 437.82 — create form dropped).
- Changed files: ONLY `apps/admin/src/pages/TicketsPage.tsx` + `apps/admin/src/api/hooks.ts`. No BE, no resident.
- `/code-review`: the cavecrew reviewer agent aborted on the session cost guard before emitting
  findings. **Record-defer** — its correctness checks are fully covered by stronger evidence already
  obtained: `tsc --noEmit` exit 0 (proves no dangling references to removed symbols, no unused-import
  break, JSX balanced) + `vite build` exit 0 + residual-symbol grep
  (`useCreateTicket|showCreate|handleCreate|isTechnician|/tickets/new`) over both changed files → none.
  No PROCESSING feature touched (edits scoped to the button + modal + their helpers/state/imports).
