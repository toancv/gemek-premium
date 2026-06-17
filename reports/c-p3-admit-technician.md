# Backlog (c) — P3: Admit TECHNICIAN to the admin portal allowed-set

**Date:** 2026-06-17
**Scope:** FRONTEND ONLY — admin app `authStore` portal-admission gate. ONE constant changed.
No route guards, nav, `homePathFor`, pages, resident app, or BE touched.
**Basis:** DECISIONS "Backlog (c) … CTO rulings (2026-06-17)" ruling 2 (D2-A: extend the existing admin portal) + ruling 8 (preserve ALL H5 invariants); H5 close-out (2026-06-16). Precondition: P2 STEP B route guards (`reports/c-p2-stepB-applied.md`) — verified present this session before the change.

---

## P2 preconditions verified BEFORE landing P3 (ruling 7 — gate must not widen on an unguarded tree)

| Precondition | State |
|--------------|-------|
| `/dashboard` guarded `[ADMIN,BOARD_MEMBER]` | ✅ App.tsx:65 |
| `/tickets` + `/tickets/:id` guarded `[ADMIN,BOARD_MEMBER,TECHNICIAN]` | ✅ App.tsx:69,70 |
| `homePathFor` backs RequireRole fallback + index + `*` + amenities + parking | ✅ App.tsx:43,51 (HomeRedirect), 64,74,76,80 |
| Layout nav: TECHNICIAN on `/tickets` only (dashboard dropped) | ✅ Layout.tsx:20,24 |
| Admin allowed-set was `[ADMIN,BOARD_MEMBER]` both gates | ✅ authStore.ts:32 → 65,82 |

All present → P3 safe to land.

---

## The change

`frontend/apps/admin/src/store/authStore.ts` line 32 — single `ALLOWED_ROLES` constant:

```diff
- const ALLOWED_ROLES = ['ADMIN', 'BOARD_MEMBER'];
+ const ALLOWED_ROLES = ['ADMIN', 'BOARD_MEMBER', 'TECHNICIAN'];
```

**Both gates covered by one edit.** The constant is referenced at BOTH H5 gate locations:
- **Bootstrap gate** (line 65): after cookie-refresh + `/auth/me`, `!ALLOWED_ROLES.includes(meRes.data?.role)` → LOCAL reset.
- **Post-login gate** (line 82): on login-response `user`, `!user || !ALLOWED_ROLES.includes(user.role)` → LOCAL reset + throw `WRONG_PORTAL`.

Changing the constant flips both gates atomically — no risk of divergence.

---

## Admin portal admission matrix (after change)

| Role | Admitted? | Outcome |
|------|-----------|---------|
| ADMIN | ✓ | Full admin app (unchanged). |
| BOARD_MEMBER | ✓ | Full admin app minus the 4 `[ADMIN]`-only pages (unchanged). |
| TECHNICIAN | ✓ **(new)** | Admitted → lands `/tickets` (`homePathFor`); every non-/tickets route bounces to `/tickets` (P2 STEP B); nav = Tickets only; no new-ticket/Assign controls. No admin-only data exposed. |
| RESIDENT | ✗ | Not in allowed-set → **LOCAL state reset only** (`accessToken`/`user`→null, `authStatus='unauthenticated'`). NEVER `/auth/logout`. Post-login throws `WRONG_PORTAL` → `getVnErrorMessage` → «Tài khoản không có quyền truy cập cổng này.» |

## Resident portal admission matrix (UNCHANGED — not touched)

`frontend/apps/resident/src/store/authStore.ts:32` still `ALLOWED_ROLES = ['RESIDENT']`.

| Role | Admitted to resident? |
|------|-----------------------|
| RESIDENT | ✓ (only admitted role) |
| ADMIN | ✗ reset-not-logout |
| BOARD_MEMBER | ✗ reset-not-logout |
| TECHNICIAN | ✗ reset-not-logout — **technician CANNOT log into the resident portal** |

---

## H5 invariants preserved (ruling 8)

- ✓ Role validated at **BOTH** gates (bootstrap + post-login) — single constant feeds both.
- ✓ Role mismatch → **LOCAL state reset only, NEVER `/auth/logout`** — bootstrap line 66, login line 83 unchanged (only the constant's contents widened).
- ✓ `WRONG_PORTAL` → `getVnErrorMessage` mapping unchanged (login lines 84-86 untouched).
- ✓ Resident allowed-set untouched — RESIDENT still the only role admitted there; TECHNICIAN rejected.
- ✓ No RequireRole / nav / `homePathFor` / page changed.

---

## Verification

- `npx tsc --noEmit` → exit 0.
- `npx vite build` → ✓ 588 modules, exit 0.
- `/code-review` (cavecrew) on the changed file — see below.

## CTO :80 smoke checklist (first live technician verification)

Rebuild required (FE change — restart will NOT pick it up):
```
docker compose up -d --build backend nginx
```

1. Technician account logs into admin :80 → SUCCESS, lands on `/tickets`.
2. Technician sees ONLY the Tickets nav item; stat cards show role-scoped counts (SLA = their overdue count, NOT 459); no new-ticket / Assign buttons.
3. Technician manually visits `/residents`, `/users`, `/reports`, `/dashboard` → each bounces to `/tickets`; no loop, no blank.
4. Technician CANNOT log into the resident portal :81 (rejected).
5. A RESIDENT still cannot log into admin :80 (rejected, LOCAL reset — not a server logout).
6. ADMIN / BOARD_MEMBER unchanged: full nav, dashboard reachable.
