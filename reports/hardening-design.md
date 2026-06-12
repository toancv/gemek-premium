# Hardening Sprint — Investigation + Remediation Design (F-04 / F-05 / SEC-20)

Status: **PROPOSAL — every choice PENDING CTO APPROVAL.** No code changed in this turn.
Date: 2026-06-12 · Investigated at HEAD `8b13e56`.

---

## A. Findings reconciliation — the two numbering schemes

There are TWO audit documents with separate numbering:

| Scheme | Document | Scope |
|---|---|---|
| `SEC-01…SEC-22` | `SECURITY_AUDIT_PROGRESS.md` | G4-era backend+frontend audit (snapshot 2026-06-02) |
| `B-xx` / `F-xx` | **`reports/security-remediation.html`** (the security-reviewer remediation log; B = backend, F = frontend findings) | remediation re-scan rounds before G4 |

**The F-xx report EXISTS — it is `reports/security-remediation.html`.** Original finding texts, quoted:

> **F-04 — Refresh token stored in localStorage — readable by XSS (MEDIUM, DEFERRED).** "The refresh token is persisted in localStorage. Any XSS vulnerability in the SPA, including from third-party scripts, can read localStorage and exfiltrate the refresh token, enabling long-lived account takeover even after session close. Remediation Plan (Hardening Sprint): Full fix requires backend changes: /auth/login and /auth/refresh must respond with Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict. Frontend removes all localStorage reads/writes of the refresh token."

> **F-05 — MinIO presign endpoint does not verify per-user file ownership (MEDIUM, DEFERRED).** "The B-05 fix confirmed that a storage key exists in ticket_photos, but does not verify the requesting user owns the ticket linked to that photo. Any authenticated user who obtains or guesses a valid key can generate a presigned download URL for another user's ticket attachment. Remediation Plan (Hardening Sprint): Add TicketPhotoRepository.existsByStorageKeyAndTicket_SubmitterId(key, userId) with a role bypass for ADMIN and TECHNICIAN."

**F-04 ≡ SEC-20: YES — same issue, different numbering.** SEC-20 (SECURITY_AUDIT_PROGRESS.md:25, severity INFO, "Refresh token in localStorage — frontend architectural decision, deferred") and F-04 (MEDIUM) describe the identical finding; the two audits scored it differently (INFO vs MEDIUM). DECISIONS.md "CTO Overrides 2026-06-03" already records the knowing deferral. This sprint treats **F-04/SEC-20 as ONE item**.

---

## B. F-05 — verified CURRENT state and remediation proposal

### B.1 What changed since the audit: the original hole is ALREADY CLOSED

The audit predates N3. At N3 P5 (commit 96ae285), `assertPresignAccess` was split onto a dedicated `enforcePhotoAccess` (TicketServiceImpl.java:883–903). The presign path today:

`GET /api/files/presign?key=` (FileController.java:56–69, authenticated) → `assertPresignAccess` (TicketServiceImpl.java:781–789): `photoRepository.findByFileUrl(key)` → **404 if no DB row** (B-05/SEC-01 fix) → `enforcePhotoAccess(photo.getTicket(), …)`:
- RESIDENT → must be an **active resident of the ticket's apartment** (403 otherwise; the public flag grants NO photo access — G8 gate).
- TECHNICIAN → assigned to the ticket OR ticket status NEW.
- ADMIN / BOARD_MEMBER → unrestricted.

This IS the per-user ownership check F-05 demanded (stronger than its proposed `Ticket_SubmitterId` fix — household-based, matching the ratified household-shared visibility model). Heart-pair test exists: `TicketPublicAccessTest.publicTicket_outsiderPresignDenied_butRedactedDetailReadable`.

### B.2 Full file-flow audit (today)

| Surface | State |
|---|---|
| **Upload** — `POST /tickets/{id}/photos` (uploadPhotos, TicketServiceImpl.java:580–660) | RESIDENT: own-apartment tickets only + phase BEFORE only; staff: any ticket. Max 5 files / 10 MB; magic-byte MIME validation (not client Content-Type). Key = `tickets/{ticketId}/{phase}/{randomUUID}{ext}` — **random UUID per file: unguessable, no overwrite possible**. ⚠ Gap: no per-role upload-presign concern (uploads stream through the BE, no upload-presign endpoint exists — good). |
| **Download presign** | As B.1. 404-on-unknown-key prevents probing; key randomness makes guessing infeasible. |
| **The presigned URL itself** | `FileStorageService.presign` (:75–90): GET, **1-hour expiry**, MinIO signature. Inherent property: **a leaked presigned URL is fetchable by ANYONE for up to 1h, no auth** — this is how presign works; only mitigations are shorter expiry and not displaying raw URLs outside `<img>` contexts. |
| **Other file surfaces** | **None live.** Contract attachments: `contracts.attachment_url` column + DTO field exist, but the spec'd `POST/GET /api/contracts/{id}/attachment` endpoints are **NOT implemented** (no controller code — spec:1392/1406 divergence, recorded here). Announcements/avatars: no file fields at all. |

### B.3 What remains open (the ACTUAL residual findings)

1. **R-1 (LOW): 1-hour presign window.** Leaked URL = 1h anonymous access. Ticket photos render inline immediately; 1h is generous.
2. **R-2 (LOW): TECHNICIAN NEW-status rule.** Any technician can presign photos of ANY ticket in status NEW (pre-assignment triage rule). Defensible, but wider than "assigned"; flag for CTO.
3. **R-3 (POLICY): the G8/N2 gates.** Public-ticket photos and N2 rich-content images are blocked ONLY by policy pending "F-05" — since the IDOR core is fixed, the remaining work is defining what those features may expose (below), not closing a hole.
4. **R-4 (HYGIENE): spec'd contract-attachment endpoints unimplemented** — when built, they MUST route through an `assertPresignAccess`-style ownership check; add to spec as a normative note.

### B.4 Proposed remediation (each PENDING CTO APPROVAL)

- **P-A Presign expiry 1h → 10 minutes** (single constant in FileStorageService). Photos are fetched immediately on detail render; 10 min covers slow connections. Re-render = new URL. Risk: long-open tabs refetching after expiry get 403 from MinIO → FE already refetches detail on focus (TanStack default).
- **P-B Keep key scheme as-is** (random UUID path — already strong). No change.
- **P-C Per-surface access matrix codified in API-SPEC** (normative): ticket photos = household + assigned-technician + admin/board; future contract attachments = staff only; N2 announcement images = **public-read by design** (announcements are broadcast content — store in a separate `announcements/` key prefix; presign check routes by prefix). This UNBLOCKS:
  - **N2 rich content**: announcement images carry no privacy expectation → safe to expose to all authenticated users (or even via long-expiry URLs) once the prefix-routing check exists.
  - **Public-ticket photos**: REMAINS BLOCKED by G8 policy (photos show home interiors) — recommend keeping the block permanently; if CTO wants opt-in "public photos", that needs a NEW per-photo creator consent flag, not a presign change.
- **P-D TECHNICIAN rule decision** (R-2): keep NEW-status access (triage need) or restrict to assigned-only. Recommend KEEP (matches list/detail scoping; changing it would desync three rules).

**Verdict proposal: re-classify F-05 as RESOLVED (by N3 P5) with follow-ups P-A/P-C** — the named finding's attack (any authenticated user presigns any known key) is no longer possible.

---

## C. F-04/SEC-20 — verified current state and the two options

### C.1 Current state (both apps identical pattern)

- FE: refresh token in `localStorage['gemek_refresh']` (resident authStore.ts:44–81, admin same store pattern); access token in memory (Zustand). Axios 401 interceptor: one retry after `POST /auth/refresh {refreshToken}` (client.ts:48–60; skips `/auth/login` + `/auth/refresh` themselves). Logout removes the key.
- BE: refresh = JWT with JTI; **Redis allow-list** `refresh:{userId}:{jti}`, TTL **7 days** (application.yml:65). `POST /auth/refresh` body `{refreshToken}` → returns **access token only — NO rotation** (AuthServiceImpl.java:159–199; FE's `if (newRt)` branch is dead code). **Logout revocation EXISTS and is total**: deletes ALL the user's refresh keys (SCAN, SEC-14) + blocklists the current access JTI (AuthServiceImpl.java:205+).

### C.2 Option 1 — httpOnly cookie for the refresh token (the full fix)

BE: `/auth/login` + `/auth/refresh` set `Set-Cookie: refreshToken=…; HttpOnly; SameSite=Strict; Path=/api/auth` (Path-scoping limits CSRF surface to the two auth endpoints); `/auth/refresh` reads the cookie (body param removed); `/auth/logout` clears it. CSRF: with `SameSite=Strict` + JSON-only endpoints + CORS exact-origin allow-list, a dedicated CSRF token is arguably redundant, but belt-and-braces = require a custom header (`X-Requested-With`) on /auth/refresh. CORS: `allowCredentials=true` + EXACT origins (no `*`) in SecurityConfig — today's config must be re-checked for wildcard usage at implementation time. FE both apps: delete every `gemek_refresh` read/write (authStore + client interceptor), refresh call becomes cookie-implicit (`withCredentials: true` on the axios instances).

**Breaking risks (full honesty):**
- `Secure` flag vs local Docker **http** on ports 80/81: a `Secure` cookie is NOT sent over http → **total login lockout in the current dev/demo deployment**. Mitigation: flag driven by profile (`Secure` only in prod) — but then dev≠prod behavior, and the demo runs the dev profile permanently today.
- `SameSite=Strict` + nginx same-origin proxy: OK today (FE and API share origin through nginx); breaks if API ever moves to a separate domain (would need `None`+`Secure`).
- Mobile webviews / Safari ITP: third-party-cookie heuristics can drop cookies if origins diverge; same-origin nginx setup is safe, but this constrains future hosting.
- Multi-tab logout/refresh races: cookie is shared state — simultaneous refreshes are fine (no rotation), but IF rotation is added later, rotation+cookie+multi-tab needs a reuse-grace window.
- One full browser smoke (login/refresh-after-expiry/logout, BOTH apps) is mandatory; an access-token-expiry wait or a shortened-TTL test profile is needed to exercise the interceptor path.

### C.3 Option 2 — documented acceptance (status quo) + compensating controls

Already in place (verified, not aspirational): logout revokes ALL refresh tokens (Redis allow-list delete) + blocklists the access token; refresh validated against the allow-list on every use (stolen token dies on logout); account-deactivation kills refresh (isActive check :189). Cheap additions if accepted: shorten refresh TTL 7d → 48h (one config line; cost: re-login every 2 days) and add refresh-token ROTATION with reuse-detection (medium effort — new Redis semantics, multi-tab races). XSS posture: no third-party scripts today, React's default escaping, no `dangerouslySetInnerHTML` — **but N2 rich content will add an HTML-rendering surface, which materially weakens the "no XSS vector" assumption that acceptance rests on.**

### C.4 Recommendation (CTO must ratify — architecture decision)

**Option 1 (httpOnly cookie), scheduled LAST in the sprint, with the profile-driven Secure flag.** Reasoning: the same-origin nginx topology makes this the cheapest moment it will ever be; N2's rich-content surface is approved work that erodes Option 2's core assumption; Option 2's only structural improvement (rotation) costs nearly as much as the cookie change while leaving the XSS-exfiltration channel open. If CTO prefers Option 2, the acceptance entry goes in DECISIONS.md with the 48h-TTL tightening and an explicit re-open trigger: "re-evaluate when N2 lands".

---

## D. Task breakdown (F-05 first — it unblocks features; auth change last, isolated)

| Step | Scope | Tests |
|---|---|---|
| H1 | F-05 close-out: presign expiry 1h→10min (P-A); API-SPEC access matrix + contract-attachment normative note (P-C/R-4); re-classify F-05 RESOLVED in remediation log | existing `TicketPublicAccessTest` heart-pair re-run; no new infra |
| H2 | N2 unblock prep (only if CTO approves P-C): `announcements/` key-prefix routing in `assertPresignAccess` (prefix → per-surface rule) | new unit tests: ticket-prefix keeps household rule; announcement-prefix allows any authenticated; unknown prefix → 403 |
| H3 | F-04 Option 1 BE: cookie issue/read/clear on the three auth endpoints, profile-driven Secure, CORS exact-origin + allowCredentials, X-Requested-With on refresh | `AuthControllerTest`: cookie attributes asserted per profile; refresh-from-cookie; logout clears + revokes |
| H4 | F-04 Option 1 FE (both apps, one commit per app): drop `gemek_refresh`, `withCredentials`, interceptor rework | builds + the existing login-401-no-loop regression checks |
| H5 | Browser smoke (CTO): login / idle-past-access-expiry refresh / logout / re-login, BOTH apps, dev profile (http, non-Secure cookie) | manual, scripted checklist in the H5 report |

H1/H2 independent of H3–H5; sprint can stop after H1 (or H2) if CTO picks Option 2.

## E. Open questions for CTO

1. Ratify **F-05 = RESOLVED** (N3 P5 closed the IDOR core) with follow-ups P-A (10-min expiry) + P-C (access matrix)?
2. F-04/SEC-20: **Option 1 (cookie, recommended)** or Option 2 (documented acceptance + 48h TTL)?
3. P-C: announcement images public-read-by-prefix — approve for N2 unblock?
4. Public-ticket photos: keep permanently blocked (recommended) or design per-photo consent later?
5. R-2: TECHNICIAN may presign photos of unassigned NEW tickets — keep (recommended, matches read scoping) or restrict?
6. Severity bookkeeping: record F-04≡SEC-20 unification in both audit docs at H-sprint close?
