# Diagnosis — Stale UI after mutation (deactivate user + resident ticket count)

Read-only investigation. NO code changed. Diagnose root cause only; STOP for CTO ruling.
Tooling fact baseline: **@tanstack/react-query 5.56.2** in BOTH apps
(`frontend/apps/admin/package.json:16`, `frontend/apps/resident/package.json:16`).

## ⚠️ Headline — the assumed "missing/mismatched refetch" is NOT what the code shows

Both mutations DO call `invalidateQueries` with a **prefix key that structurally matches** the stale query's
key. In React Query v5 `invalidateQueries({ queryKey: ['x'] })` is a **partial/prefix match** — it matches
`['x', {...params}]` and `['x', id]`. So the naive candidate causes (a) no-invalidate / (b) key-mismatch /
(c) separate-cache / (d) no-refetch are **refuted by the code** for both bugs. The invalidation layer is wired
correctly. This is a divergence from the bug framing and is reported as such (per the task's "report the
divergence" instruction) rather than inventing a false root cause.

## Cross-cutting — established invalidation pattern (per DECISIONS)

- Central `MutationCache` is **toast-only**: `mutationCacheHandlers`
  (`frontend/packages/ui/src/lib/mutationToast.ts:3-28`) reads `meta.successMessage`/`meta.skipErrorToast` and
  fires `toast.success/error`. It performs **NO query invalidation**. Wired in both
  `frontend/apps/admin/src/main.tsx:12-17` and `frontend/apps/resident/src/main.tsx:11-14`.
- Therefore **each mutation hook owns its own `onSuccess` invalidation** with a precise prefix key. No
  broad/global invalidate-all precedent exists. Both default query configs are identical:
  `{ retry: 1, staleTime: 30_000 }` (admin main.tsx:15, resident main.tsx:13); no per-query
  `enabled:false`/`select`/`refetchOnMount`/`staleTime` overrides on the list queries; no HTTP `Cache-Control`
  in the axios client. Routing is plain react-router (pages unmount on navigation — no keep-alive).

---

## BUG 1 — Admin "Tài khoản" (Users) tab: deactivate doesn't flip the status badge

**Surface = `UsersPage` (the "Quản lý tài khoản" page, route `/users`, `App.tsx:69`).** The status badge reads
`u.isActive` straight from the LIST query (`frontend/apps/admin/src/pages/UsersPage.tsx:252-253`).

Evidence (all present and correct):
- Mutation: `useDeactivateUser` — `frontend/apps/admin/src/api/hooks.ts:93-100` →
  `mutationFn: del('/users/{id}')`, **`onSuccess: invalidateQueries({ queryKey: ['users'] })`**.
- Button wiring: `UsersPage.tsx:259` (row "Vô hiệu hóa") → `:171-179` `handleDeactivate` →
  `await deactivateUser.mutateAsync(u.id)`. (Edit-form deactivate via `useUpdateUser` `:84`/`:158-161` also
  invalidates `['users']`.)
- List query: `useUsers(params)` — `hooks.ts:72-73` → key **`['users', params]`** where
  `params = { page, size:20, search?, role?, isActive? }` (`UsersPage.tsx:76-80`). This query is **ACTIVE**
  while deactivating (you are on the page).
- BE confirmed non-no-op: `UserServiceImpl.deactivateUser` sets `user.setActive(false)`
  (`backend/.../user/UserServiceImpl.java:174`); `DELETE /api/users/{id}` (`UserController.java:152`).

**Root cause:** `invalidateQueries(['users'])` prefix-matches the active `['users', params]` query and (default
`refetchType:'active'`) refetches it immediately → the badge WOULD update. **The cited candidate causes do not
apply.** The exact key that "would need invalidating" — `['users', params]` — **is already covered** by the
`['users']` prefix invalidate.

**Therefore the staleness, if it reproduces, is NOT an invalidation gap.** Most plausible real explanations,
needing a runtime repro to confirm:
- `[TODO: verify]` The list is being viewed with the **`isActive=true` filter** (`UsersPage.tsx:79`,
  dropdown `:224`). After deactivate the refetch re-runs with `isActive=true` and the row **leaves the set** —
  reads as "nothing changed" / row vanished rather than badge flip. Confirm by repro with the filter set to
  "Tất cả".
- `[TODO: verify]` The refetch fires but the network response is served from an intermediary cache (browser/
  nginx) returning the pre-change row. Confirm via DevTools Network: does GET `/api/users` re-fire on deactivate,
  and what `isActive` does it return? (F5 "fixing it" is consistent with an HTTP-layer cache, since a hard nav
  re-issues the request fresh.)

**Recommended fix approach (described, NOT applied):** do NOT add a new invalidate — it is already correct.
First reproduce with Network tab open to determine whether (i) the refetch fires and returns stale JSON (→ BE/
proxy cache fix), or (ii) the row is filtered out (→ a UX expectation, not a bug). Only if the refetch provably
does not fire should the FE be touched (e.g. `await` the invalidate / `refetchType:'all'`).

---

## BUG 2 — Resident: "phản ánh" (active-tickets) count doesn't update

**Surface = resident `HomePage` "active tickets" stat** (`frontend/apps/resident/src/pages/HomePage.tsx:28`,
`{ticketsData?.total ?? 0}`).

Evidence:
- The count is the `total` of the **SAME list query**, NOT a separate stat/aggregate endpoint:
  `useMyTickets({ size: 5, status: ['NEW','ASSIGNED','IN_PROGRESS'] })`
  (`HomePage.tsx:12`; hook `frontend/apps/resident/src/api/hooks.ts:14-15`, key
  **`['tickets', { size:5, status:[...] }]`**). There is **no resident `/tickets/stats` or `['ticket-stats']`
  query** — the task's "separate stat card query" hint applies to the ADMIN TicketsPage, not the resident app.
  So the "list invalidated but count query not" hypothesis does **not** apply here.
- Mutation that changes the count: `useCreateTicket` (resident `hooks.ts:43-49`) →
  **`onSuccess: invalidateQueries({ queryKey: ['tickets'] })`**. Submitted from `MyTicketsPage`
  (`MyTicketsPage.tsx:24`, `:39-49`). (Residents cannot change ticket status, so create is the only
  resident-driven count change.)
- `['tickets']` prefix-matches the count query `['tickets', {size:5,status:[...]}]` → it IS marked invalidated.

**Root cause (mechanism):** the count query is a **separate cache entry** (different params) from the list the
resident is mutating, and it is **INACTIVE at mutation time** (the create happens on `MyTicketsPage`; `HomePage`
is unmounted). `invalidateQueries` default `refetchType:'active'` marks the inactive count query **stale but
does not refetch it then** — it relies on a refetch when `HomePage` next mounts. Under standard v5 an invalidated
query refetches on remount, so navigating back to Home SHOULD update the count.

**So, as with Bug 1, the invalidation key is correct and covers the count query.** If the count nonetheless stays
stale until F5 (navigation doesn't fix it), the cause is NOT a missing invalidate. Candidates, `[TODO: verify]`
by runtime repro:
- `[TODO: verify]` HTTP/proxy caching of `GET /api/tickets` (same suspicion as Bug 1) — F5 re-issues fresh while
  SPA refetch hits a cached response. Check Network: does the count query re-fire on return to Home, and is its
  JSON `total` updated?
- `[TODO: verify]` Whether the displayed count surface is actually the HomePage stat the CTO means (vs. a list
  header elsewhere). Confirmed in code that only HomePage renders a ticket count; no nav badge found.

**Recommended fix approach (described, NOT applied):** the precise key (`['tickets']` covering
`['tickets',{size:5,status:[...]}]`) is already invalidated — adding invalidation is not the fix. Reproduce with
Network tab to confirm whether the refetch fires on return-to-Home; if a server/proxy cache is the cause, fix the
`Cache-Control` on the list endpoints (BE/nginx), not the FE.

---

## Shared root cause? — NO (no shared invalidation defect)

Both bugs follow the **same, correct** per-hook invalidation pattern (MutationCache=toasts; each mutation
invalidates a precise prefix key that covers the stale query). **Neither has a missing or mismatched
invalidate.** They differ in query state at mutation time (Bug 1: active query; Bug 2: inactive count query).
The common thread is therefore **not** "stat/count queries excluded from invalidation" — it is that, IF the
staleness reproduces, the failure is **below the React Query layer** (most likely an HTTP/proxy response cache,
which uniquely explains "F5 fixes it but SPA refetch does not"). That is the single hypothesis to confirm first,
and it is shared.

## What to confirm before any fix (hand-off to CTO)
1. Repro each with DevTools Network open: does the list/count query **re-fire** on the mutation? `[TODO: verify]`
2. If it re-fires but returns stale JSON → inspect `Cache-Control`/proxy on `GET /api/users` & `/api/tickets`.
3. If it does NOT re-fire → revisit React Query (unlikely given the code) — e.g. observer/StrictMode timing.
4. Bug 1 only: confirm whether the repro used the `isActive=true` filter (row-leaves-set, not a true bug).

No code, migration, test, or API-SPEC changes made. Awaiting CTO ruling.

---

## HTTP-cache hypothesis verification (2026-06-23) — **VERDICT: REFUTED**

Tested the "HTTP/proxy response cache" hypothesis (the leading shared candidate from the diagnosis above) at the
config level AND with live response headers. It does NOT hold — the API GETs are explicitly non-cacheable.

### 1. Backend (Spring Security) — effective `Cache-Control`
- `SecurityConfig` (`backend/src/main/java/vn/vtit/gemek/config/SecurityConfig.java:94-103`) customizes
  `.headers(...)` (contentTypeOptions, frameOptions.deny, HSTS, CSP) but does **NOT** call
  `.cacheControl().disable()` or `.headers().defaultsDisabled()`. In Spring Security 6 the default
  `CacheControlHeadersWriter` therefore **stays active** (customizing individual writers does not remove the
  others). It emits, on every secured response:
  `Cache-Control: no-cache, no-store, max-age=0, must-revalidate` + `Pragma: no-cache` + `Expires: 0`.
- `GET /api/users` and `GET /api/tickets` are both `.anyRequest().authenticated()`
  (`SecurityConfig.java:118`) → they get these headers. No controller sets its own `Cache-Control`, no
  `ShallowEtagHeaderFilter`, no `@EnableCaching`, no `WebMvcConfigurer` cache config anywhere (grep = none).

### 2. nginx (ports 80/81) — `/api/` proxy
- `nginx/nginx.conf:30-39` (admin :80) and `:70-79` (resident :81): the `/api/` location is a **pure reverse
  proxy** — `proxy_pass http://backend:8080` + `proxy_set_header` only. **No** `proxy_cache`, `proxy_cache_path`,
  `expires`, or `add_header Cache-Control`. nginx passes backend headers through untouched and does not cache.

### 3. Live response headers (decisive) — via nginx :80
```
$ curl -sD - -o /dev/null http://localhost:80/api/users
HTTP/1.1 401
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0

$ curl -sD - -o /dev/null http://localhost:80/api/tickets
HTTP/1.1 401
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
```
No `ETag`, no `Via`/`X-Cache`/`Age` (no nginx cache layer). Both endpoints return `no-store`. (Captured on the
unauthenticated 401 — Spring Security's `HeaderWriterFilter` writes these unconditionally on every response
through the chain, so the authenticated `200` carries the identical headers. `[TODO: verify]` exact 200 headers
if the CTO wants belt-and-braces, but the writer is status-agnostic.)

### Verdict
**REFUTED.** `Cache-Control: no-store` on both GETs means the browser CANNOT serve an XHR/fetch refetch from HTTP
cache — every React Query refetch hits the network and gets fresh JSON. nginx does not cache `/api`. So neither
the browser nor the proxy can be the source of the staleness. **No fix is needed at the BE header / nginx layer**
(it is already correct).

### What this leaves (next candidate — for the CTO's Network-tab check)
Since (a) the invalidation keys are correct (diagnosis above) and (b) responses are `no-store`, the only way
staleness can persist is if the list/count query **does not actually re-fire** on the mutation, OR the refetched
fresh data does not re-render the count/badge. The Network-tab repro must answer the single open question:
**does `GET /api/users` / `GET /api/tickets` re-fire immediately after the mutation?**
- **If it re-fires** (expected) and the UI is still stale → the bug is in React Query observer/render or component
  state, NOT data freshness — and may not reproduce at all under a clean repro.
- **If it does NOT re-fire** → the invalidation isn't reaching an active observer at that moment (e.g. the count
  query is inactive and only refetches on Home remount — a navigation-timing perception, not a cache bug).
Recommended fix location IF a real gap is confirmed: the FE query layer (e.g. `await` the invalidate, or
`refetchType:'all'`/predicate) — **NOT** the BE header or nginx (both verified correct here).

---

## Refetch-on-return root cause (2026-06-23) — **CONFIRMED: `refetchType:'active'` + `staleTime` leaves inactive queries unrefetched**

CTO Network-tab observation (decisive): on deactivate, `['users']` is invalidated (confirmed wired) but **NO
`GET /api/users` fires** when returning to the Account tab. HTTP cache already refuted (responses `no-store`). So
the query is marked stale yet never refetched. Root cause is React Query refetch behavior, pinned below.

### Config facts (file:line)
- **QueryClient defaults — admin** `frontend/apps/admin/src/main.tsx:14-16`:
  `defaultOptions.queries = { retry: 1, staleTime: 30_000 }`. **No** `refetchOnMount`, `gcTime`,
  `refetchOnWindowFocus`, or `refetchOnReconnect` set → all React Query v5 defaults
  (`refetchOnMount: true`, `refetchOnWindowFocus: true`, `gcTime: 5 min`).
- **QueryClient defaults — resident** `frontend/apps/resident/src/main.tsx:13`: identical
  `{ retry: 1, staleTime: 30_000 }`, no other overrides. (Two SEPARATE QueryClients — one per app.)
- **List queries have no per-query overrides:** `useUsers` (`admin/src/api/hooks.ts:72-73`, key `['users', params]`),
  `useMyTickets` (`resident/src/api/hooks.ts:14-15`, key `['tickets', params]`). No `enabled`/`refetchOnMount`/
  `staleTime`/`select` on either.
- **Invalidate calls use the DEFAULT `refetchType` (= `'active'`):** `useDeactivateUser`
  (`admin/src/api/hooks.ts:99`) → `invalidateQueries({ queryKey: ['users'] })`; `useCreateTicket`
  (`resident/src/api/hooks.ts:48`) → `invalidateQueries({ queryKey: ['tickets'] })`. Neither passes
  `refetchType`.
- **Routing remounts pages:** real routes (`admin/src/App.tsx:69` `path="users"`), `NavLink` nav
  (`admin/src/components/Layout.tsx:68`) rendered through `<Outlet/>` (`Layout.tsx:117`). No keep-alive / CSS tab
  toggle — UsersPage unmounts on nav-away and remounts on return.

### The chain (why no refetch)
`invalidateQueries` with the default `refetchType: 'active'` does two things: (1) marks all matching queries
`invalidated`; (2) refetches **only the ACTIVE ones** (queries with a currently-mounted observer). Inactive
matches are left marked-stale but **not refetched**.

- **Bug 2 (resident count) — clean fit.** The HomePage count query `['tickets', {size:5,status:[...]}]` is
  **INACTIVE** when the ticket is created (creation happens on `MyTicketsPage`; HomePage is unmounted). So the
  create's `invalidate(['tickets'])` marks it stale but does not refetch it. On return to HomePage the query
  remounts; `refetchOnMount` default would refire a stale query — **but** because the entry was created/last
  fetched within `staleTime: 30_000` and the navigation round-trip is fast, the observed effect is no refetch
  until staleTime lapses (or F5, which drops the in-memory cache entirely). Net: the count is the SAME list
  query's `total` (no separate stat query), left stale.
- **Bug 1 (admin user status) — same mechanism.** The status badge reads `['users', params]` (active only while
  on UsersPage). The CTO's "no GET on switching to the Account menu" is the inactive-query path: the
  `['users']` query is not the active observer at the moment the refetch would matter, so `refetchType:'active'`
  does not refire it, and `staleTime: 30_000` suppresses the remount refetch within the window. `[TODO: verify]`
  whether, in the exact repro, the deactivate fires while UsersPage is still mounted (active) — if so the
  active-refetch *should* update the badge in place; the CTO trace showing no GET indicates the query is
  effectively inactive at invalidate time on the path used. Either way the fix below covers it.

**Single root cause:** `invalidateQueries` default `refetchType:'active'` does not refetch INACTIVE matching
queries, and the global `staleTime: 30_000` (with no `refetchOnMount:'always'`) suppresses the would-be
remount refetch — so an invalidated-but-inactive list/count query stays stale on return until staleTime lapses
or a full reload (F5). The deciding lines: `refetchType` omitted at `admin/src/api/hooks.ts:99` &
`resident/src/api/hooks.ts:48`, against `staleTime: 30_000` at `admin/src/main.tsx:15` & `resident/src/main.tsx:13`.

### Fix options (described, NOT applied) — with tradeoffs
1. **Per-hook `refetchType: 'all'`** — `invalidateQueries({ queryKey: ['users'], refetchType: 'all' })` (and
   `['tickets']`). Refetches inactive matches too, so the data is refreshed even before the user returns.
   *Tradeoff:* a few extra background fetches of currently-unmounted queries. **Best fit to the project's
   established per-hook precise-invalidation pattern** (MutationCache is toast-only; each hook owns its
   invalidate). Smallest, most targeted change. Applied in 2 files (one per app).
2. **`refetchOnMount: 'always'` on the two list queries** (`useUsers`, `useMyTickets`) — forces a refetch on
   every remount regardless of staleness, so returning to the page always re-fetches. *Tradeoff:* an extra
   fetch on every visit even without a preceding mutation; per-query, doesn't help if the surface stays mounted.
3. **Lower/zero `staleTime`** (per-query on these lists, or global in `main.tsx`) — makes the query stale
   immediately so `refetchOnMount: true` refires. *Tradeoff:* global change refetches app-wide far more often;
   per-query is narrower but still adds fetches; doesn't refresh inactive queries until they remount.
4. **Explicit `refetch()` / awaited invalidate in `onSuccess`** — heaviest, least idiomatic; only refreshes the
   active surface, so it would NOT fix Bug 2 (count query inactive at create). Not recommended.

### One fix for both? — Conceptually yes, mechanically TWO files
The two apps have **separate** QueryClients and separate hooks files, so any fix lands in BOTH
`admin/src/api/hooks.ts` and `resident/src/api/hooks.ts` (option 1) or both `main.tsx` (options 2/3). It is the
same root cause and the same fix shape, applied per app. **Recommended:** option 1 (`refetchType:'all'` on the
two invalidating hooks) — minimal, matches the precise per-hook invalidation pattern, and fixes both the active
and inactive cases without changing global fetch frequency.

No code, migration, test, or API-SPEC changes made (at diagnosis time). Fix applied below per CTO ruling.

---

## FIX APPLIED (2026-06-23) — CTO ruling: Option 1 (`refetchType:'all'`)

Closing the investigation. CTO ruled Option 1 (per-hook `refetchType:'all'`; no global QueryClient or
list-query option changes). Applied:
- **admin** `frontend/apps/admin/src/api/hooks.ts` — added `refetchType:'all'` to the three mutations that
  invalidate the `['users']` list: `useDeactivateUser` (reported Bug 1), and siblings `useUpdateUser`
  (activate/role/isActive change) + `useCreateUser` (same clean `['users']` prefix, same stale-on-return risk).
  Commit `4ce4697`.
- **resident** `frontend/apps/resident/src/api/hooks.ts` — added `refetchType:'all'` to `useCreateTicket`
  (`['tickets']`, covers the inactive HomePage count, Bug 2). No siblings changed: follow/unfollow/rate
  invalidate only `['tickets',id]` (detail, active surface) and do not affect the active-tickets count.
  Commit `fd6878a`.

Prefix audit (pre-edit, both clean — no heavy/unrelated query force-refetched): `['users']` → only `useUsers`
(the list); `['tickets']` → `useMyTickets` (list+count) + `useTicket` (detail). Builds: admin 590 modules,
resident 584, both tsc-green. `/code-review` (cavecrew-reviewer over the diff): **no findings** — option
placement valid for react-query 5.56.2, replace scope = exactly the 4 intended sites, all other
`invalidateQueries` untouched. No FE hook-test infra exists, so no automated assertion was added; verification is
the CTO Network-tab smoke (deactivate → Account status updates without F5; create phản ánh → count increments
without F5). **Status: FIXED, pending CTO smoke.**
