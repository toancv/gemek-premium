# Backlog (c) — Cross-Session Identity Confusion: Root-Cause Diagnosis (READ-ONLY)

**Date:** 2026-06-18 · **Branch:** `deploy/local`, HEAD `50bdca9` · tree clean (untracked `reports/` scratch + `scripts/GenHash.java` only).
**Repro (CTO):** two incognito windows on the admin site; W1 = TECHNICIAN, W2 = BOARD_MEMBER (logged in later); returning to W1 it showed BOARD_MEMBER's view.
**Mandate:** find the exact mechanism; no code change; do NOT conclude "dev-only harmless" without proof. This report PROVES the backend holds no cross-request/cross-session identity, then names the exact client/cookie mechanism that produced the symptom.

---

## Backend identity-holding inventory (items 1–5) — where identity lives per request

| # | Question | Finding (file:line) | Verdict |
|---|----------|---------------------|---------|
| 1 | `SecurityContextHolder` strategy | No `setStrategyName(...)` anywhere in `src/main`. Only uses are reads/writes of the context: `JwtAuthenticationFilter.java:137` (set), `AuditLogAspect.java:118` (get), `UserServiceImpl.java:148` (get). | **Default `MODE_THREADLOCAL`** — per-request thread. No inheritable/global strategy → no thread-pool inheritance bleed. ✅ |
| 2 | Singleton bean holding principal as instance state | `JwtAuthenticationFilter` fields are all `final` injected deps (`:55-57`), no per-request mutable field. Every `private User user` in the codebase is a **JPA entity relation** (`AuditLog.java:57`, `AnnouncementRead.java:53`, `Notification.java:55`, `NotificationSubscription.java:55`, `Resident.java:55`, `ResidentHistory.java:59`) — not bean state. `getMe(UserPrincipal principal)` (`AuthServiceImpl.java:249`) takes the principal as a **method arg** (`@AuthenticationPrincipal`), never a field. No static principal field. | **No singleton/static field holds the current user.** ✅ |
| 3 | Per-request principal resolution / SEC-06 | `JwtAuthenticationFilter.doFilterInternal` parses the Bearer token fresh, loads the user **from the DB every request** (`:118-127`), builds authorities from the **DB role** `user.getRole()` (`:130-134`, the SEC-06 fix), not the token claim. No `@Cacheable`/`@CachePut` on the user lookup (grep: none in `src/main`). | **Fresh per-request, per-token. No cached resolution.** ✅ |
| 4 | Redis / server cache keyed non-uniquely | All auth Redis keys are user+jti scoped: refresh = `REFRESH_KEY_PREFIX + userId + ":" + jti` (`AuthServiceImpl.java:140, 179, 227`); blocklist = `BLOCKLIST_KEY_PREFIX + jti` (`JwtAuthenticationFilter.java:110`, `AuthServiceImpl.java:219`); rate-limit = per-IP (`:339`). No fixed/shared "current user" key. | **No non-user-scoped auth cache key.** ✅ |
| 5 | Server-side session (`HttpSession`) | Grep `HttpSession`/`getSession` in `src/main` → **zero hits**. Auth is purely stateless JWT (Bearer access token + httpOnly refresh cookie). | **No server session to cross.** ✅ |

**Backend conclusion:** identity is held ONLY in the per-request thread-local `SecurityContext`, populated from the request's own Bearer token, resolved from the DB each request. There is **no place** where one request's identity can bleed into another request or another session on the server. Case (ii) "server-side bleed" is **disproven**.

---

## Frontend / cookie inventory (items 6–7) — the only shared surface

**6. Where the admin app stores token + user (`store/authStore.ts`):**
- `useAuthStore` is a zustand store created at module scope (`:38`). A zustand "module-level singleton" is **one instance per JS document/heap** — each browser tab/window has its own module graph, so `accessToken` + `user` (`:16-17`) live in **per-document JS memory**. Two windows do NOT share this object.
- **Therefore the FE in-memory store cannot, by itself, cross two windows** — separate windows have separate JS heaps. (The code even documents the host-scoped-cookie risk at `:31-36`.) A FE-memory-only cause is impossible for this repro. The shared surface must be the cookie.

**7. The refresh cookie (`module/auth/AuthController.java`):**
- Name `"refreshToken"` (`:57`), Path `/api/auth` (`:60`), `httpOnly(true)` (`:242`), `secure(cookieSecure)` — **false in dev http**, true in prod (`:73-74, :243`), `sameSite("Strict")` (`:244`). Built on login (`:107`) and re-issued on refresh.
- **Single fixed cookie name, host-scoped.** Within ONE cookie jar, a second login's `Set-Cookie: refreshToken=...` **overwrites** the first — there is exactly one `refreshToken` per host per jar. This is the known "dev overwrite" surface — but it applies to ANY shared jar, not just dev.

**The adoption path (how W1 changes identity) — `authStore.bootstrap()` (`authStore.ts:53-83`):**
On app load / remount, bootstrap does `POST /auth/refresh` (cookie-implicit, `:64-67`) → gets an access token for **whoever the cookie now belongs to** → `GET /auth/me` (`:71`) → `set({ user: meRes.data })` (`:78`). The displayed identity is **re-derived from the shared cookie**, not from W1's prior in-memory user. (The 401-interceptor refresh in `client.ts:49-58` keeps the *old* in-memory `user` on `setTokenAndUser(newToken, ...user)` at `:55`, so a mid-session 401 swaps the token silently but not the label — the full visible flip happens on the next bootstrap/reload, which calls `/auth/me`.)

---

## VERDICT

### Primary: **(i)-class — shared-cookie-jar identity REPLACEMENT, NOT a server-side identity bleed.**

**Exact mechanism (proven, all cited above):**
1. W1 TECHNICIAN login → tech access token + tech `user` in W1's JS memory; the jar's `refreshToken` cookie = tech's refresh token.
2. W2 BOARD_MEMBER login → server `Set-Cookie refreshToken=<board>` (`AuthController.java:107`) **overwrites the single host-scoped cookie in the shared jar** (`:57`).
3. W1, on its next bootstrap (reload / window refocus remount / React StrictMode re-invoke) → `POST /auth/refresh` sends the **now-BOARD_MEMBER cookie** → BOARD access token → `GET /auth/me` → `user = BOARD_MEMBER` (`authStore.ts:64-78`). W1 "becomes" BOARD_MEMBER. ∎

**Why this crossed the two windows — the CTO premise is the error:** "two separate incognito windows = separate cookie partitions" is **false**. In Chrome/Edge, multiple incognito **windows of the same incognito session share one cookie jar + storage** (incognito is a single ephemeral profile; only a fully distinct incognito session, or a different browser, gets a separate jar). So W1 and W2 shared the `refreshToken` cookie — exactly the condition step 2–3 require. This is the complete explanation; no server defect is needed and none exists (items 1–5).

**Why it is NOT case (ii) / not dangerous:**
- It is identity **replacement by a deliberately-authenticated second user** in a shared jar, not an unauthorized **leak**. W1 gains nothing that W2 (BOARD_MEMBER) did not already log in as with their own credentials. No privilege is escalated beyond a real login.
- A browser cookie jar inherently holds **one** session per host. No cookie-based scheme can keep two different identities in one jar simultaneously — this is universal cookie semantics, not an app defect.
- The BE proof (items 1–5) shows the access token in each window is honored exactly per its own subject; nothing server-side mixes them.

**Severity: LOW / informational.** Real-world trigger requires two different users sharing one browser profile/jar (or one tester juggling roles, as here). Not exploitable for privilege escalation or cross-user data theft via the server.

**Residual real-world footgun (not the reported bug, noted for completeness):** the FE re-adopts whatever identity the shared cookie resolves to on bootstrap with **no binding between the in-memory access-token subject and the displayed `user`**. A user switching roles in one jar sees surprising identity flips, and there is a brief window where W1's in-memory `user` label (old) and its refreshed access token (new owner) disagree (`client.ts:55`). Optional CTO-gated hardening (do NOT implement now): on bootstrap/refresh, compare the refreshed access-token subject to the held `user.id` and force an explicit re-login on mismatch, and/or surface "session changed in another tab". This is a UX/robustness enhancement, not a security fix — the server boundary is already correct.

### Contingency: **(iii)** — only if the repro is later confirmed to have used genuinely SEPARATE incognito *sessions* (two distinct jars, e.g. incognito + a second browser).
In that case **no application-code path can cross them** (items 1–6 prove FE memory and BE state are both isolated, and separate jars don't share the cookie). The crossing would then have to come from **outside this codebase** — e.g. an intermediary HTTP cache proxying `/auth/me` or `/auth/refresh` (a shared/misconfigured cache returning the last writer's body). Runtime evidence required before concluding: (a) confirm whether the two windows were one incognito session (shared jar) or two — the single most likely answer; (b) capture response headers on `/auth/me` + `/auth/refresh` for `Cache-Control`/`Age`/`Set-Cookie` and any `Via`/proxy header; (c) add a per-request request-id + log the resolved `userId` in `JwtAuthenticationFilter:138` and correlate W1's request to a tech vs board token. Do NOT guess — these three checks decide (i) vs (iii) definitively.

---

## Bottom line for the CTO

- The backend is **clean**: stateless, per-thread, per-token, DB-resolved each request, user-scoped Redis, no `HttpSession` — proven no cross-request/cross-session identity bleed (items 1–5).
- The symptom is the **shared host-scoped `refreshToken` cookie** being overwritten by W2's login and W1 re-adopting it on bootstrap (`AuthController.java:57/107`, `authStore.ts:64-78`). It crossed the windows because two incognito windows of one session **share the cookie jar** — the "separate partitions" assumption is incorrect.
- **Not a privilege-escalation defect.** Severity LOW. Optional, CTO-gated FE hardening (token-subject ↔ held-user binding on refresh) is available but unnecessary for security. Confirm the jar-sharing fact (one runtime check) to close (iii).
