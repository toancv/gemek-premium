# Backlog (c) — P2 STEP B applied: per-page RequireRole + role-aware landing + nav filter

**Date:** 2026-06-17
**Scope:** FRONTEND ONLY (admin app). No role admitted to the portal (that is P3, still forbidden).
No BE change. H5 invariants (ALLOWED_ROLES / authStore gates / WRONG_PORTAL / local-reset) untouched.
**Basis:** `reports/c-p2-route-audit.md` §4 (STEP A plan, CTO-approved Option 2) + `reports/c-p3-BLOCKER-p2-gap.md`.

> **Phase-order correction:** P2 STEP B was DESIGNED in STEP A but never shipped — the P2.5/2.6/2.7
> ticket-stats work jumped ahead of it. This entry ships the route-guard audit late, reconciled with
> the P2.5–2.7 tree (stat cards + drill-down + «Trễ hạn ✕» chip left fully intact).

---

## Files changed

| File | Change |
|------|--------|
| `src/lib/homePathFor.ts` (NEW) | `homePathFor(role)`: TECHNICIAN → `/tickets`, else `/dashboard`. |
| `src/App.tsx` | Guard `/dashboard` `[ADMIN,BOARD_MEMBER]`; guard `/tickets` + `/tickets/:id` `[ADMIN,BOARD_MEMBER,TECHNICIAN]`; `RequireRole` fallback → `homePathFor(user?.role)`; new `HomeRedirect` for index, `*`, deferred `/amenities` + `/parking`. |
| `src/components/Layout.tsx` | Dashboard nav entry: dropped TECHNICIAN → `[ADMIN,BOARD_MEMBER]`. Tickets keeps TECHNICIAN. |
| `src/pages/LoginPage.tsx` | Both `/dashboard` navigate literals (already-auth bounce + post-login) → `homePathFor(role)`. |
| `src/pages/TicketsPage.tsx` | New-ticket button hidden from TECHNICIAN (BE create = ADMIN+RESIDENT). Stats/drill-down/chip untouched. |
| `src/pages/TicketDetailPage.tsx` | Assign "Phân công" card hidden from TECHNICIAN (BE assign = ADMIN-only). Status-update kept (BE = ADMIN+TECHNICIAN). |

No `/dashboard` redirect literal remains outside the `/dashboard` route element + its `[ADMIN,BOARD_MEMBER]`
RequireRole. Grep `'/dashboard'` in routing/redirect code → only the literal route path + nav entry + NOTIF_ROUTES (bell deep-link, role-neutral, not a landing redirect).

---

## 4-role × all-routes access matrix (after STEP B)

Legend: ✅ renders · → /tickets (role-aware fallback) · → /dashboard · n/a (pre-auth). Portal admission gate
(authStore `ALLOWED_ROLES=[ADMIN,BOARD_MEMBER]`) is UNCHANGED — RESIDENT and TECHNICIAN cannot yet log into
admin (P3 pending). Matrix below is the **route-guard layer** assuming a session of each role exists.

| Route | ADMIN | BOARD_MEMBER | TECHNICIAN | RESIDENT* |
|-------|-------|--------------|------------|-----------|
| `/` (index) | → /dashboard | → /dashboard | → /tickets | → /dashboard |
| `/dashboard` `[A,B]` | ✅ | ✅ | → /tickets | → /dashboard→login‡ |
| `/tickets` `[A,B,T]` | ✅ | ✅ | ✅ **(lands here)** | → /dashboard |
| `/tickets/:id` `[A,B,T]` | ✅ | ✅ | ✅ | → /dashboard |
| `/apartments` `[A,B]` | ✅ | ✅ | → /tickets | → /dashboard |
| `/residents` `[A]` | ✅ | → /dashboard | → /tickets | → /dashboard |
| `/users` `[A]` | ✅ | → /dashboard | → /tickets | → /dashboard |
| `/contractors` `[A,B]` | ✅ | ✅ | → /tickets | → /dashboard |
| `/announcements` `[A]` | ✅ | → /dashboard | → /tickets | → /dashboard |
| `/vehicles` `[A]` | ✅ | → /dashboard | → /tickets | → /dashboard |
| `/reports` `[A,B]` | ✅ | ✅ | → /tickets | → /dashboard |
| `/amenities` (deferred) | → /dashboard | → /dashboard | → /tickets | → /dashboard |
| `/parking` (deferred) | → /dashboard | → /dashboard | → /tickets | → /dashboard |
| `*` (unknown) | → /dashboard | → /dashboard | → /tickets | → /dashboard |

\* RESIDENT can never hold an admin session (authStore gate). Column shown for completeness only.
‡ A RESIDENT would also be reset by the authStore gate before any route renders; the `/dashboard`→login
hop only matters if a non-admitted role somehow held a session, which the gate prevents.

**Result:** TECHNICIAN reaches ONLY `/tickets` + `/tickets/:id`, and lands on `/tickets` from every
other entry point. ADMIN reaches everything (unchanged). BOARD_MEMBER reaches everything except the
4 `[ADMIN]`-only pages (unchanged from today — no loosening, no tightening).

---

## Redirect-loop trace (TECHNICIAN, the new path)

1. Technician (post-P3) logs in → `LoginPage` → `homePathFor('TECHNICIAN')` = `/tickets`.
2. `/tickets` → `RequireRole [ADMIN,BOARD_MEMBER,TECHNICIAN]` → role included → **renders**. STOP. ✅
3. Technician manually visits `/dashboard` → `RequireRole [ADMIN,BOARD_MEMBER]` → not included →
   `Navigate homePathFor('TECHNICIAN')` = `/tickets` → step 2 renders. **One redirect, no loop.** ✅
4. Same for `/residents`, `/users`, `/reports`, `/apartments`, `/contractors`, `/announcements`,
   `/vehicles`, `/amenities`, `/parking`, `*`, `/` → all resolve to `/tickets` in one hop, which admits. ✅

**Why no loop is structurally possible:** every redirect target is either `/tickets` (admits TECHNICIAN)
or `/dashboard` (admits ADMIN/BOARD). The landing route for a given role ALWAYS admits that role —
`homePathFor` maps each role to a route whose RequireRole includes it. No role is sent to a route that
rejects it, so no second redirect fires. `user` is guaranteed present inside `RequireRole`/`HomeRedirect`
(RequireAuth holds render until `authStatus==='authenticated'`, which the authStore only sets together
with `user`), so `homePathFor(undefined)` (→ /dashboard) is unreachable from the authenticated tree.

---

## In-page admin-only control audit

BE `@PreAuthorize` ground truth (`vn/vtit/gemek/module/ticket/TicketController.java`, read-only):

| Action | Endpoint | BE roles | TECHNICIAN? | Handled |
|--------|----------|----------|-------------|---------|
| Create ticket | `POST /api/tickets` | ADMIN, RESIDENT | ❌ 403 | **Hidden** (TicketsPage new-ticket button `!isTechnician`). |
| Assign ticket | `PUT /{id}/assign` | ADMIN only | ❌ 403 | **Hidden** (TicketDetailPage "Phân công" card `!isTechnician`). |
| Update status | `PUT /{id}/status` | ADMIN, TECHNICIAN | ✅ allowed | **Kept** (core technician work). |
| Stat cards / drill-down / chip | `GET /api/tickets` (`?status/category/overdue`) | A,B,T,R | ✅ allowed | **Untouched** — role-neutral, role-scoped on BE. URL-param seeding intact. |

**Pre-existing FE/BE mismatch (OUT OF SCOPE — flagged, not fixed):** BOARD_MEMBER sees the create
button, the assign card, and the status-update form, but the BE rejects all three for BOARD_MEMBER
(create=ADMIN+RESIDENT, assign=ADMIN, status=ADMIN+TECHNICIAN) → 403 on submit. This predates P2 STEP B
and is unrelated to the technician scope; the task constrains "ADMIN/BOARD unchanged from today," so
BOARD's view is deliberately left as-is. Recommend a separate ruling to gate these to BOARD's true BE
permissions. Record-defer.

---

## Verification

- `npx tsc --noEmit` → exit 0.
- `npx vite build` → ✓ 588 modules (was 587; +1 = `homePathFor`), exit 0.
- P2.5/2.7 stat block + drill-down + chip: code-traced intact — `TicketStats`/`StatCard`/`SlaCountCard`/
  `CategoryCountRow` unchanged; `useSearchParams` single-source-of-truth + `drillDown`/`setFilter`/`goPage`
  untouched; built into the bundle. Only the sibling new-ticket button gained a role guard.
