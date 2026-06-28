# DECISIONS — Apartment Management System

Log of all autonomous decisions made by agents.
Format: Date | Decision | Reasoning | Alternatives

---

## 2026-06-28 | Contractor-document oversize → HTTP **413** (sanctioned divergence from C3's 400)

**Decision.** The contractor-document service-cap-too-large condition (`CONTRACTOR_DOCUMENT_TOO_LARGE`) returns **HTTP 413**, and the FE consolidates ALL oversize signals (per-file, per-contractor, total) onto 413. C3's announcement attachment path returned 400 for its app-level cap; the contractor path deliberately uses 413 (semantically correct for "payload too large", and aligns the app-level cap with the servlet/multipart 413 the FE already maps). **This is an intentional, logged divergence — NOT a drift.** P3's documents manager will therefore treat 413 (no coded body) as the single oversize message, the same belt-and-suspenders the announcement managers' `errorText` already uses for the servlet 413.

**Scope note (P2, this commit):** P2 ships FE create/edit PAGES only (no upload UI), so no 413 handling is wired yet — this entry pre-records the contract P3 will consume. **Alternatives considered:** match C3's 400 (rejected — 413 is the correct status and avoids a second app-vs-servlet split on the FE).

---

## 2026-06-26 | Trunk = `main` (rename `deploy/local`→`main`; retire the 3-commit `master` skeleton)

**Decision (pending CTO runbook execution; see `reports/git-trunk-rename-runbook.md`).** Rather than consolidating
524 commits onto the stale 3-commit `master` skeleton, **`deploy/local` itself becomes the trunk, renamed `main`**.
`deploy/local` already IS the full linear history (descends from master's 3 commits; all other branches —
`improvement/security`, `phase/backend`, `phase/frontend`, `master` — are ancestors/fully merged, 0 unique
commits), so no merge/consolidation is needed; the `master` skeleton is **retired** (kept-for-history or deleted
per CTO). **This SUPERSEDES the earlier "consolidate onto master" framing** in
`reports/git-branch-topology.md` / the prior DECISIONS entry below.

**Pre-flight secret audit = SAFE to trunk:** no real `.env` tracked (only `.env.example`; `.env` gitignored at
`.gitignore:6`); all prod secrets are `${ENV_REF}` in `application.yml`. Two non-blocking, dev-only items (CTO
call): `docker-compose.dev.yml:10` hardcoded **dev** DB password (parameterize or accept dev-only);
`scripts/seed-demo-local.sql` committed **demo** bcrypt hashes (keep demo-only, never seed prod). Optional:
gitignore `docker-compose.override.yml`.

**Go-forward (unchanged from the workflow convention, now based off `main`):** feature-per-branch off `main`
before coding → work + smoke + `/code-review` + close → **push the feature branch + STOP; CTO opens the PR**.
Agent NEVER pushes/merges `main` (PR-protected). All rename/push/branch-delete/GitHub-default/protection ops are
**CTO-executed** per the runbook — the agent does not perform them.

---

## 2026-06-26 | Go-forward branch workflow + git topology (master is a 3-commit skeleton)

**Finding (see `reports/git-branch-topology.md`).** `origin` = `github.com/toancv/gemek-premium`. `master`
exists but is a **3-commit skeleton** (agentic-SDLC setup + architecture design + `CLAUDE.md`) with **NO
application code**; `master` is a linear **ancestor** of `deploy/local`, which is **524 commits ahead** and
holds the **entire app**. So per-feature PRs onto the current master are not viable (base files absent) — the
deploy/local→master gap is the whole product and needs a **one-time consolidation/sanitization PR** (CTO),
not cherry-picks. Phone-search commits are internally clean (no config files) but cannot reach master in
isolation; they land inside that consolidation.

**Workflow convention (CONFIRMED, go-forward):**
- Each new feature / large backlog item → checkout its **OWN branch** from `<base — CTO to confirm; likely
  `deploy/local` until master is consolidated>` BEFORE coding; work + smoke + `/code-review` + close on the branch.
- On close → **push the FEATURE BRANCH** to `origin` and **STOP**; the **CTO opens the PR to master**. The agent
  **NEVER** merges to or pushes `master` (PR-protected). Even `deploy/local` pushes are the CTO's call.
- **Phone-search is the last exception** (done on `deploy/local`, no branch) — reaches master via a CTO-opened PR
  per the topology report.
- `deploy/local` carries **local-only config that must NOT reach master unparameterized**: `docker-compose.yml`/
  `.dev.yml`, `application-dev.yml`, `nginx/nginx.conf` (localhost/:8090), `scripts/seed-demo-local.sql` + demo
  seed. `.env.example` is fine; real `.env` stays gitignored.

---

## 2026-06-26 | Phone added to list `search` filter (residents/users/contractors) — substring, no normalization

**Decision (CTO ruling, locked; BE-only).** The `?search=` param of the three ADMIN list endpoints now ALSO
matches the **stored phone** via plain case-insensitive substring (`lower(phone) LIKE %lower(:q)%`) — NO
canonicalization of either the query or the stored value. Phone was ADDED to each endpoint's existing OR group
(name/email for residents+users; company name/contact person for contractors), not replacing the existing
matched fields. All three build the predicate via the **Criteria API**, so this respects the locked
Hibernate-null-safe rule (no JPQL with a nullable param); none needed conversion or a STOP-flag. `Contractor.phone`
is nullable, but `cb.like` over a NULL column evaluates to NULL (no match, never an error — the pattern is
non-null), so no `isNotNull` guard was added (mirrors the existing email LIKE). **Alternative rejected:**
normalizing the search term to canonical phone before matching — rejected per CTO (substring-only).

---

## 2026-06-26 | C3 P3 (FE resident) — announcement attachments = flat gated download list (C3 CLOSED)

**Decision (CTO rulings, locked; FE-only this phase).** The resident `AnnouncementDetailPage` renders the
detail response's `attachments[]` as a **flat download list** below the markdown body (and cover banner),
ONLY when non-empty. Each row = document icon + `displayFilename` + human-readable size + a "Tải về" anchor
to the BE-minted `downloadUrl`.

**Why these choices:**
- **Flat list, NOT inline, NOT through `MarkdownContent`** — documents are downloaded, never rendered; the
  renderer's safe-img surface stays untouched (no new XSS surface).
- **Access is BE-gated, never client-guessed** — `attachments[]` is scope-gated server-side by the same C2.1
  `assertMediaPresignAccess` rule as media; an out-of-scope resident receives an EMPTY array → no section.
  The FE renders only what it received (no IDOR: it never sends an attachment/announcement id the principal
  didn't already get in its own gated detail response).
- **Download forced server-side (C3 P1)** — `downloadUrl` already carries the signed
  `response-content-disposition=attachment` + `application/octet-stream`, so the FE needs NO disposition
  handling; a plain anchor downloads. The `download` attribute is belt-and-suspenders only. **No
  `target=_blank` inline-preview** (CTO ruling; rejected the code-review suggestion to add it).
- **Resident pattern, NOT admin's** — mobile-first list styling; no admin top-right toast (read-only list
  needs no feedback). `href` scheme-guarded (`^https?://`) → non-http renders an inert disabled span.

**Deferred (logged debt, non-blocking):** `formatSize` + `safeHref` + the attachment-row markup + the "Tải về"
label are duplicated with admin's `AnnouncementAttachmentsManager`; a shared `@gemek/ui` `formatBytes` /
`isHttpUrl` / `AttachmentList` would centralize them (mirrors the existing C2.3b http-guard-dup debt). NOT
done now to keep C3 P3 from widening into the shared package before the gate. **Admin's `formatSize` has the
same latent KB→MB rounding boundary bug fixed here in resident** — fix when the shared extraction happens.

**API-SPEC unchanged** — C3 P1 already documented detail `attachments[] = {id, displayFilename, sizeBytes,
downloadUrl}`. **C3 CLOSED** (P1 BE + P2 admin manager + P2.5 lazy-save + P3 resident download list).

---

## 2026-06-26 | Rule — a client-side block that renders OFF-SCREEN from its trigger gets an admin toast (+ keep inline)

**Decision (CTO ruling, locked; FE pattern).** When a client-side rejection's message renders far from the
control the user just acted on, ALSO surface it via the locked admin top-right `toast.error` (`@gemek/ui`
`{ Toaster, toast }`, `<Toaster position="top-right" />` in `App.tsx`) — the toast says WHY, while the existing
inline error stays and marks WHICH field (toast is IN ADDITION, never a replacement). When the message already
renders at the point of action (visible to the user), keep it inline ONLY — do NOT toast (avoid toast spam).
**First application (C3 P2.5):** a lazy upload on `/announcements/new` blocked by an invalid create form now
toasts (the managers are at the page top; `form.formError` renders at the bottom). The other /new pick
rejections — oversize >10MB, total >50MB, count ≥5 — render right at the pick button, so they stay inline (no
toast). No behaviour/contract change; no draft, no upload on the blocked path (unchanged).

---

## 2026-06-26 | C3 P2.5 (FE admin) — lazy-save on /new (first upload auto-creates draft) — implements CTO ruling A

**Decision (CTO ruling A, locked; FE-only, NO backend/contract change).** On `/announcements/new` BOTH the
image media manager and the attachments manager now render (same as `/:id/edit`) — showing their upload
controls + constraints + empty state (grid/list/insert/delete only have content post-upload, by which point
we've already navigated to edit). Because uploads need a draft id and `/new` has none, the create page
provides an `ensureDraftThenUpload` orchestrator wired into each manager via a new optional `onLazyUpload`
prop. On the FIRST upload (image OR attachment) with no id: **validate** the create form (same `validate()`
as "Lưu nháp": title/content/scope + conditional block/floor) → if invalid, NO draft + NO upload, the inline
VN error shows; if valid → **POST /announcements** (draft with current form values) → **upload** the file to
that id → **`navigate('/announcements/{id}/edit', { replace: true })`** so the edit page refetches and shows
the result. The edit page's direct-upload path is UNCHANGED (id exists → no create; managers omit
`onLazyUpload` → existing `mutateAsync` runs). "Lưu nháp"/"Tạo & đăng" keep the C2.3b save-first behaviour.

**No-orphan + single-draft guarantees.** A draft is created ONLY on a real upload or an explicit save —
merely visiting/leaving `/new` creates nothing. The **oversize file-size pre-check runs in the manager BEFORE
`onLazyUpload`**, so an over-cap file is rejected without creating a draft (no orphan from an invalid file —
preserves the 2026-06-26 oversize-fix ordering). A **synchronous `inFlight` ref** (not state) guards BOTH
`submit()` and `ensureDraftThenUpload`: a second trigger in the same tick sees it set and bails, so a
double-click or a quick second picked file creates EXACTLY ONE draft. `lazyBusy` state + `externalBusy={busy}`
on both managers disable every upload trigger and both save buttons while a lazy-save is in flight (the ref is
the real guarantee; the disabled UI is cosmetic).

**Partial-failure (reuses the C2.3b create→publish recovery shape).** create FAILS → `setFormError`, abort, NO
upload, NO draft. create OK + upload FAILS → STILL `navigate(replace)` to `/:id/edit` (the draft IS saved) with
a `state.notice` carrying the upload error (the edit page already renders `notice`) — never strands the admin
on `/new`, never deletes the draft.

**`/code-review` (high, workflow) — 16 findings; APPLIED 1, rest by-design/debt.** APPLIED (correctness): the
lazy path no longer seeds the detail cache with the pre-upload create response — `created` has EMPTY
media/attachments, so seeding flashed an empty edit page until the upload's refetch landed (the just-uploaded
file looked lost). Leaving the cache unseeded lets the edit page's `useAnnouncement` fetch FRESH detail (which
already includes the uploaded item, since the upload completes before navigate) behind its spinner. The
explicit-save `submit()` KEEPS its seed (no upload there → `created` is the accurate full state).
**By-design / accepted (not bugs — they implement the locked ruling):** (a) picking a file on an invalid form
"drops" it (input reset) and shows the inline error — this is the mandated "validate before upload, no draft"
behaviour; the admin re-picks after filling the form. (b) a second rapid pick during an in-flight lazy-save is
intentionally rejected by the single-draft guard. (c) upload-failure feedback is surfaced on the EDIT page via
`state.notice` (not the manager's inline error) — this is the mandated navigate-on-upload-fail recovery; the
edit page renders it. **DEBT (logged, non-blocking):** (1) no "Đang tải lên..." progress label on the /new
managers during a lazy upload (the page's upload hook is pending, not the manager's; buttons are disabled =
feedback) — adding a dedicated `uploading` prop would misfire during an explicit draft-save, so deferred.
(2) the 413→"10MB" size string is now in three places (both managers' `errorText` + the page's
`uploadErrNotice`) — a shared `@gemek/ui` helper would centralise it (same DRY debt the managers already
carry from the oversize fix). (3) `ensureDraftThenUpload` has no `finally` reset of `inFlight`/`lazyBusy` — it
relies on `navigate(replace)` unmounting `/new`; not constructible today (every post-create exit navigates and
no route guard blocks it), the create-fail exit DOES reset. (4) validation error renders at the page bottom
while the managers sit at the top — consistent with the edit-page layout (CTO-locked); scrolling the error
into view on a /new lazy-validation-fail would improve it.

**Verification.** admin `tsc --noEmit` clean; admin `vite build` green (765 modules; the known transient
Windows esbuild temp-file unlink lock cleared on retry — not a code error). `@gemek/ui` UNTOUCHED (no vitest
re-run needed). Admin has **no vitest harness** → no admin unit test. **API-SPEC unchanged** (no endpoint
change; create/upload endpoints already documented in C3 P1/C2.2).

**Alternatives rejected.** (a) Orchestrator only "ensures id" while the manager keeps owning upload + the
post-upload navigation — rejected; spreads the create/upload/navigate/error routing across the manager
boundary and complicates the upload-fail-still-navigate recovery. The manager delegates the validated file;
the page owns create→upload→navigate→errors. (b) Seed the cache then patch it after upload — rejected; the
unseeded fetch is simpler and always correct (upload is done before navigate). (c) Delete the draft on upload
failure — rejected by the ruling (the draft survives; the admin retries on edit).

---

## 2026-06-26 | Multipart oversize handling — clean 413 + finite swallow + FE pre-validate (all uploads)

**Decision (CTO rulings, locked; applies to EVERY multipart upload).** Fixes the C3 P2 oversize-upload hang
(`reports/c3-attachment-oversize-hang-diagnosis.md`). Three coordinated changes:

1. **BE handler → 413.** `GlobalExceptionHandler` now handles `MaxUploadSizeExceededException` (+
   `MultipartException` fallback) → **HTTP 413** with the standard coded body. The servlet multipart limit
   fires in `DispatcherServlet.checkMultipart` BEFORE routing, so the surface is inferred from the request
   path: `/attachments` → `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE`, `/media` → `ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED`,
   else generic `PAYLOAD_TOO_LARGE` (new `ErrorCode`, 413). Status 413 is what matters — the FE's existing
   413-branch renders a size message off the status, not the body code. Previously unhandled → generic 500.

2. **Tomcat `server.tomcat.max-swallow-size: 60MB`** (finite, ≥ the 55MB `max-request-size`), NOT `-1`.
   Without a swallow allowance Tomcat resets the connection mid-upload for a STREAMING browser, so even a
   clean 413 never arrives → the "đang tải lên" hang. 60MB lets it drain the in-flight remainder of an
   over-cap-but-under-edge upload and deliver the 413. **Finite, not -1**, so anything past the nginx edge
   (`client_max_body_size 20m`) stays edge-rejected rather than drained unbounded (DoS guard). `max-file-size`
   10MB / `max-request-size` 55MB unchanged; nginx unchanged (20m edge-reject of >20MB is acceptable).

3. **FE pre-validate before upload (both managers).** `AnnouncementAttachmentsManager` rejects instantly when
   `file.size > 10MB` or running-total + size > 50MB (it has per-item `sizeBytes`); `AnnouncementMediaManager`
   rejects `file.size > 10MB` per file (its media manifest carries NO per-item size → the 50MB total stays
   BE-enforced, returning a clean `ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED`). Count cap (5) already disables the
   button. So no oversize request is sent for honest clients; the BE 413 (1)+(2) is the backstop for any
   bypass/non-browser client.

**Scope:** this is multipart-wide (images + attachments + any future upload), not C3-specific — same servlet
path, handler, and swallow default. **Verification:** backend suite **454/454** (+3 `GlobalExceptionHandlerMultipartTest`
asserting 413 + path-mapped code); `@gemek/ui` vitest 33 (PAYLOAD_TOO_LARGE VN string + exhaustive guard);
admin tsc + build green; HTTP smoke — 11MB upload now returns a clean **413** (was 500). API-SPEC notes 413 on
the upload endpoints. **Prod:** the handler + swallow are app-level (every environment); FE pre-check is
environment-independent.

**Alternatives rejected.** (a) `max-swallow-size: -1` (unlimited) — rejected by CTO (drain-the-world DoS); a
finite 60MB ≥ max-request-size suffices and keeps the edge cap meaningful. (b) Per-controller multipart
exception handling — rejected; the limit fires before routing, so a global advice with path inference is the
only place it can be caught. (c) FE-only fix — rejected; a non-browser/bypass client still needs a clean
server 413, and the swallow reset must be fixed for the browser path regardless.

---

## 2026-06-26 | C3 P2 (FE admin) — attachments manager on the EDIT page only (create-page lazy-save = P2.5)

**Decision (CTO rulings, locked; FE-only, NO backend/contract change).** A new
`AnnouncementAttachmentsManager` is mounted as a sibling of `AnnouncementMediaManager` on
`/announcements/:id/edit` (`AnnouncementEditPage.tsx`), DRAFT-only (same guard as the image manager —
the page blocks editing a published announcement entirely). It is a **flat downloadable list**, NOT a
thumbnail grid and NOT body-placeholder content: each row = file icon + `displayFilename` +
human-readable size + "Tải về" (anchor to the detail manifest's forced-download `downloadUrl`) + "Xoá"
(VN confirm). ONE "Tải lên tệp đính kèm" button (no cover/inline kind); accept hint
`.pdf,.docx,.xlsx,.pptx,.txt` (the BE Tika magic-byte check is authoritative). Disabled at the 5-file cap.

**Placement = EDIT only this phase.** The **create page (`/new`) is NOT wired** — attachments (like media)
need a draft id, and `/new` is lazy-save (no id until first save). Wiring uploads on `/new` (create the
draft on first upload, then attach) is **P2.5**, deferred per CTO. The component is **id-driven** (takes
`announcementId` + `attachments[]`) so P2.5 mounts it post-save with no rework.

**Reuse + independence.** New hooks `useUploadAnnouncementAttachment` (FormData `file`, no kind) /
`useDeleteAnnouncementAttachment`, both invalidating `['announcements', id]` (`refetchType:'all'`) so the
list + presigned URLs refresh — same pattern as the media hooks. The LIST comes from the existing
`useAnnouncement` detail query's `attachments[]` (no new list call). Error mapping reuses the media
manager's HTTP-413 special-case + `getVnErrorMessage`. Added the 3 missing VN strings
(`ANNOUNCEMENT_ATTACHMENT_{TYPE_NOT_ALLOWED,TOO_LARGE,LIMIT_EXCEEDED}`) to `@gemek/ui` `errorMessages.ts`
(+3 vitest assertions + exhaustive-guard array). The image media manager, body editor, preview, and scope
logic are **UNTOUCHED**; attachment caps/counter are INDEPENDENT of the image caps. **No body placeholder
strip** on delete (attachments are never inline). Notify flags unchanged. **API-SPEC unchanged** (C3 P1
already documented the endpoints + `attachments[]`).

**Verification.** admin `tsc --noEmit` clean; admin `vite build` green (765 modules; a transient Windows
esbuild temp-file lock on the first two runs, passed on retry — not a code error); `@gemek/ui` vitest
**33 passed** (errorMessages). Admin has **no vitest harness** → no admin unit test (renderer/contract
unaffected). `/code-review` (high) run on the FE diff.

**/code-review (high) — applied + debt.** APPLIED FE-fixable: (2) **download anchor scheme guard** — `href`
is bound only when `^https?://` matches (mirrors the resident page's defense-in-depth), else an inert
disabled span — closes a `javascript:`/`data:` click vector if the manifest URL were ever contaminated;
(3-partial) modal `role="dialog"`/`aria-modal`/`aria-labelledby` + **Escape-to-close**; per-row
`aria-label` on "Tải về"/"Xoá"; hidden file input `aria-hidden`+`tabIndex=-1` + upload button `aria-label`.
**DEBT (logged, not blocking):** (a) full modal **focus-trap** — the new dialog AND the existing
`AnnouncementMediaManager`/`AnnouncementsPage` hand-roll modals without focus management; the right fix is
reusing `@gemek/ui` `Modal` across all three (already-logged C2.3b P2 debt), NOT diverging one component;
(b) **no ESLint** in the frontend workspace (react-hooks/jsx-a11y never run) — pre-existing project-wide gap,
separate chore. Both deferred to avoid widening this tight FE phase.

**Alternatives rejected.** (a) Wire `/new` create-page uploads now — deferred to P2.5 (lazy-save complexity;
out of this phase's CTO scope). (b) Thumbnail grid like images — rejected; documents render as a labeled
download list. (c) Strip a body placeholder on delete — N/A (attachments have no inline placeholder).

---

## 2026-06-26 | C3 P1 (BE) — announcement file attachments: separate table + forced-download (CTO rulings, locked)

**Decision (CTO rulings, locked; BE-only this phase).** Downloadable DOCUMENT attachments on announcements,
DISTINCT from C2.x cover/inline images. **Architecture B (separate table)** — new `announcement_attachment`
(migration `V22`, mirrors V21: `announcement_id` FK **ON DELETE CASCADE**, `object_key` TEXT on the SAME C2.1
key convention `announcements/{id}/{uuid}` so the existing presign gate parses it unchanged, `display_filename`
NOT NULL, `created_by` FK SET NULL, index on `announcement_id`; NO `kind` — attachments are a flat list). The
image media table/caps/validation/manifest path is **UNTOUCHED**. Endpoints `POST/GET/DELETE
/api/announcements/{id}/attachments` parallel the media trio (ADMIN, draft-only; GET ADMIN/BOARD). Detail
`GET /announcements/{id}` gains `attachments[] = {id, displayFilename, sizeBytes, downloadUrl}`, gated by the
SAME `assertMediaPresignAccess` scope rule (out-of-scope/denied → empty, no leak, no 500).

**Serving posture (all four, security-required).** (1) Presigned URL forces
`response-content-disposition=attachment; filename=…`; (2) `response-content-type=application/octet-stream` —
both signed into the SigV4 query via a NEW **additive** `FileStorageService.presign(key, disposition, type)`
overload (the existing `presign(key)` image path is byte-for-byte unchanged → images stay inline); (3) nginx
:8090 front adds `add_header X-Content-Type-Options "nosniff" always;` (applies to all objects); (4) Tika
magic-byte allow-list blocks renderable/executable types at upload. Filename sanitized for the
`Content-Disposition` header per RFC 6266/5987 (`ContentDispositionUtil`: ASCII fallback strips quote/CR/LF/
control, `filename*` percent-encodes UTF-8) so a filename can't inject header/query-param content.

**Allowed types (magic-byte):** pdf, docx, xlsx, pptx, txt. OOXML (docx/xlsx/pptx) is a ZIP container that
**tika-core 2.9.1 cannot subtype** (it returns `application/zip`) — disambiguated by peeking the zip's part
layout (`word/`|`xl/`|`ppt/`), still pure content inspection, NO heavy `tika-parsers` dep, NO extension trust.
**csv LIMITATION (flagged for CTO confirm at smoke):** csv content is byte-identical to plain text, so
magic-byte detection reports `text/plain` and a csv is accepted/stored AS txt. The literal ruling said "reject
csv"; that is **impossible via magic bytes** without trusting the extension (which the ruling also forbids).
Chosen posture: accept text/plain (txt allowed); forced-download + nosniff neutralize the only browser-relevant
risk (csv-injection is a downstream spreadsheet-app concern on local open, not a serving risk). NOT silently
resolved — surfaced for the CTO ruling.

**Caps (INDEPENDENT of image caps):** ≤10 MB per file (service-level, belt-and-suspenders with the unchanged
servlet `max-file-size=10MB` — gives a coded `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` even on a direct service call),
≤5 attachments, ≤50 MB total per announcement. New error codes `ANNOUNCEMENT_ATTACHMENT_{TYPE_NOT_ALLOWED,
TOO_LARGE,LIMIT_EXCEEDED}` (all 400). Draft delete now collects BOTH media AND attachment object keys for the
after-commit cleanup (no orphaned objects). `created_by` via the existing `CreatableEntity`/`SecurityAuditorAware`
auditing (no new code).

**Verification.** Full backend suite **451/451** (was 422, +29): `ContentDispositionUtilTest` (5; quote/CRLF/
Vietnamese/null), `FileStorageServiceDownloadPresignTest` (2; download URL carries the signed params, image
presign carries none), `AnnouncementAttachmentServiceIntegrationTest` (13; type allow/reject incl.
html-renamed-.pdf + plain-zip + svg, caps count/per-file/total, image-caps-independence, draft-only,
delete+cascade media&attachments, forced-download manifest), `AnnouncementAttachmentManifestScopeTest` (3;
in/out-scope resident + admin draft), `AnnouncementControllerTest` (+4; 201/403/409/400). Sequential,
order-independent. Prod-delivery cookie-scope/subdomain stays **[PLANNED]/[TODO]** (out of scope). FE (P2/P3)
NOT started. API-SPEC updated same phase.

**Alternatives rejected.** (a) Extend `announcement_media` with an ATTACHMENT kind (option A) — CTO chose B
(clean separation, zero risk to the image caps/validation/manifest). (b) Add `tika-parsers-standard-package`
for OOXML subtyping — rejected (pulls POI/PDFBox/BouncyCastle, heavy image bloat for pure type detection; the
zip-peek is sufficient and dependency-free). (c) Extension-based reject of csv/html/svg — rejected for the
magic-byte allow-list (html/svg ARE distinguishable by bytes and rejected; csv is not — documented limitation
instead of an extension-trust hole). (d) Raise the global servlet `max-file-size` — rejected (would loosen the
image path); the per-file attachment cap is enforced in-service instead.

---

## 2026-06-26 | Authoring layout — row-aligned 2-col MIRROR (supersedes "title full-width on top")

**Decision (CTO ruling, locked; FE style only).** The admin announcement composer (`AnnouncementComposeFields`,
shared by `/new` + `/:id/edit`) is a row-aligned 2-column mirror: ONE CSS grid (`grid-cols-1 lg:grid-cols-2`,
`items-start`) with the Soạn/Xem trước section headers (row 0) then THREE rows that align left↔right —
(1) **cover slot** at a FIXED admin-side height (`h-40`): left = a subtle dashed hint "Ảnh bìa quản lý ở Thư
viện ảnh phía trên" (not a control), right = the cover banner if present else a muted "Chưa có ảnh bìa" box of
the same height (so title/body stay aligned regardless of cover state); (2) **title**: left = the "Tiêu đề"
input (now INSIDE the left column, ~half width), right = the preview `<h1>`; (3) **body**: left =
label+toolbar+textarea, right = the markdown render. The compact Loại/Phạm vi/Tòa/Tầng selects stay below
(conditional Tòa/Tầng preserved).

**Supersedes** the close-out "Tiêu đề full-width on top" layout and the post-fix "title under the banner in a
single preview box" — the title is no longer full-width and the preview is no longer one stacked box; cover,
title, and body are now three aligned grid rows.

**Admin-side banner height + cover-slot hint.** The fixed banner height (`h-40`) is applied ADMIN-SIDE (a
wrapper around the `<img>` in the composer), NOT by editing the shared `@gemek/ui` `MarkdownContent`/renderer —
the cover banner in the preview is composer-rendered, not via the markdown renderer. The left cover-slot hint
exists purely to keep the left column's title/body rows aligned with the right when no cover control lives on
the left (cover is managed in the full-width media library above).

**Responsive.** A single grid can't both row-align on desktop AND cleanly stack on mobile, so mobile uses
Tailwind `order-*` to regroup cells into compose-first then preview-second; `lg:order-none` restores the
DOM-order column pairing for the desktop row mirror. (/code-review-applied: the mobile interleave and a
title-preview-vs-input vertical misalignment.)

**No BE/contract/`@gemek/ui`/resident change; API-SPEC unchanged.** Commit `d4e99bd` (style). admin + resident
tsc+build green (shared components untouched).

---

## 2026-06-26 | Post-C2.3b — image insert COLLAPSES the caret (no nested-image markdown); shared renderer was not the bug

**Decision (locked rule).** Announcement image insertion (`AnnouncementComposer.insertImage` → shared
`spliceSelection(..., collapseAfter=true)`) MUST collapse the caret to AFTER the inserted snippet, NOT leave
the placeholder selected. **Rationale:** leaving the alt selected (the toolbar `insertMarkdown` behaviour)
caused a 2nd consecutive "Chèn vào bài" to wrap the first placeholder's still-selected alt → nested markdown
`![![…](b)](a)`; CommonMark flattens the inner image into the outer alt, so only one image rendered. **The
shared `@gemek/ui` `MarkdownContent` renderer was diagnosed and found CORRECT** — well-formed adjacent
placeholders always render two `<img>` (regression test locks this); the fix therefore stayed in the admin
composer and the renderer/XSS posture (scheme gate, allow-list, no rehype-raw) was NOT touched, so resident
detail is unaffected. Toolbar formatting keeps select-after (type-to-replace) via the same primitive with
`collapseAfter=false`. A real text selection still becomes the image alt (no data loss).

**Accepted trade-off.** Image insert with NO selection ships the generic alt "mô tả ảnh" (the auto-select of
that generic alt for type-to-replace was dropped — re-selecting it would reopen the nesting class). A user who
selects descriptive text first still gets a meaningful alt. The composer insert has no unit test (admin has no
vitest harness — out of scope to stand up); the renderer-contract regression test + CTO smoke cover it.

**Also (FE-only, same batch):** preview renders the title (`<h1>`) below the cover banner (mirrors resident
`AnnouncementDetailPage`); Loại/Phạm vi/Tòa/Tầng selects made compact. No BE/contract change; API-SPEC unchanged.
Commits: `886365e` fix · `5398a93` test(ui) · `81ac27c` feat · `6e41cd4` style.

---

## 2026-06-26 | C2.3b CLOSE-OUT — cover-UX final shape (two upload buttons) + delete-strips-placeholder + layout + cover banner (C2.3b CLOSED)

**Decision (CTO rulings, locked). FE-only, NO backend/contract change** (`docs/API-SPEC.md` unchanged). Four
refinements close C2.3b:

1. **Cover-UX FINAL = two kind-specific upload buttons, NO checkbox** (supersedes P2's single button + "Đặt
   làm bìa" checkbox). "Tải lên ảnh bìa" uploads `kind=COVER`; "Tải lên ảnh bài viết" uploads `kind=INLINE`.
   Still kind-at-upload — **no new BE endpoint**; cover-replace (2nd COVER replaces the 1st in-tx) is
   unchanged C2.2 behaviour. The implementation uses a `kindRef` set by whichever button opened the shared
   hidden file input. **Cap interaction:** the ≤5-image / ≤50MB caps are shared across both kinds (the
   `x/5 ảnh` counter counts all media). The INLINE button disables at the 5-cap; the COVER button stays
   enabled at the cap **only when a cover already exists** (`hasCover` — a replace is net-zero), and disables
   at the cap when none exists (a net-new 6th would exceed the cap). [/code-review CONFIRMED fix — the first
   cut gated COVER on `busy` only, allowing a net-new 6th past the cap.]

2. **Media delete now STRIPS the inline placeholder from the body** (supersedes P2's deliberate leave-as-is).
   On a successful delete, every `![alt](announcement-media:{deletedId})` occurrence is removed from the editor
   content, keyed by id (robust to edited alt text + repeats). The strip is an **UNSAVED editor-state change**
   — the admin still clicks "Lưu" to persist, identical to any other body edit; the confirm dialog VN text now
   says the insertion will be removed and to save. A COVER id never appears in the body → no-op for cover
   deletes. **Regex hardened (/code-review CONFIRMED):** a naive `!\[[^\]]*\]` cannot match a placeholder whose
   alt contains `]` (CommonMark allows balanced brackets in image alt); replaced with a tempered-token pattern
   `!\[(?:(?!\]\(scheme)[^\n])*\]\(scheme:id\)` anchored on the unique `](scheme:id)` suffix, so a `]`-in-alt
   is handled and the match never spans into a *different* placeholder. Insert + strip both build off the
   shared `ANNOUNCEMENT_MEDIA_SCHEME` constant from `@gemek/ui` so the two formats can't drift.

3. **Layout (style):** "Tiêu đề" spans full width on top; below it the [body editor | preview] row is
   top-aligned (each column leads with a label + a same-height toolbar/spacer so the textarea and the preview
   box start at the same Y — fixes the CTO-flagged preview floating up to title level); Loại/Phạm vi/Tòa/Tầng
   moved full-width below the row. Pure layout — no field/behaviour change, committed separately as `style(admin)`.

4. **Preview COVER banner (the remaining P3):** the preview pane now renders the COVER manifest entry as a
   banner above the markdown body, **mirroring the resident `AnnouncementDetailPage.tsx:40-47`** (same
   `^https?://` defense-in-depth guard before binding to `src`; no cover → no banner). Inline images still
   resolve in the body; the cover is NOT duplicated inline.

**Verification + /code-review (high, workflow-backed).** admin `tsc --noEmit` + `vite build` green; `@gemek/ui`
vitest untouched this phase. Admin has **no vitest harness** → no admin unit tests. Review returned **0 Must-fix**;
5 FE-fixable applied pre-commit (regex tempered token; cover-cap `hasCover` gate; per-kind busy label via
`kindRef`; module-level `EMPTY_MANIFEST` default; shared `ANNOUNCEMENT_MEDIA_SCHEME` in insert+strip). Debts
deferred (non-blocking, logged in PROGRESS): unsaved-strip-vs-BE-delete divergence (BY DESIGN per this ruling),
`^https?://` guard duplication vs resident (a shared `isHttpUrl`/`toMediaManifest` helper would centralize it —
deferred to avoid widening into the shared pkg + resident), cover `find`-first on transient duplicate COVER
(harmless), `AnnouncementMediaItem` vs `AnnouncementMediaManifestEntry` DRY.

**Commits.** `2a3f4b0` `style(admin)` layout → `38e7196` `feat(admin)` close-out (two-button + delete-strip +
cover banner) → this docs entry. Kept separate per the commit-grouping rule; style landed first because the
cover banner sits inside the restructured preview (layout is its substrate).

**C2.3b CLOSED** (P1 + P1.5 + P2 + close-out all done) — awaiting CTO :80 smoke. NEXT = **C3 (file attachments)**.

**Alternatives rejected.** (a) Keep the single button + checkbox — rejected by CTO for two explicit buttons
(clearer intent, no sticky-toggle bug class). (b) Leave the body placeholder on delete (P2 behaviour) —
superseded; the CTO wants the insertion gone with the image. (c) Auto-persist the body strip via an extra PUT
on delete — rejected; would diverge from the "edits are unsaved until Lưu" model and could ship a body change
the admin didn't intend.

---

## 2026-06-26 | C2.3b P2 — announcement media manager (FE-only) + P2/P3 boundary shift (inline preview folded into P2)

**Decision (CTO rulings, locked).** Media manager built on `/announcements/:id/edit` for a DRAFT only
(upload/delete are draft-only on the BE). NOT on `/new` (save-first — no id yet) and NOT for a published
announcement (P1's read-only block stays). Three locked sub-rulings implemented as specified:
1. **Unified manager — ONE grid** lists every uploaded image, thumbnails from the **detail manifest**
   (`GET /announcements/{id}` → `media[]`, ADMIN-on-draft is non-empty/presigned). INLINE item = "Chèn vào
   bài" + "Xoá"; COVER item = a BÌA badge + "Xoá" **only — no insert** (cover renders as a banner, never in
   the body). No separate "list" call — the manifest already carries the presigned thumbnail URLs.
2. **Cover = kind-at-upload toggle** ("Đặt làm bìa": off→INLINE, on→COVER). Uploading a COVER replaces the
   old cover (C2.2 in-tx). The toggle is reset to off after each successful upload so the *next* upload
   doesn't silently re-replace the cover (a /code-review CONFIRMED bug, fixed pre-commit).
3. **Insert reuses `insertMarkdown`** (the preserved ref+RAF from P1's composer) to drop
   `![mô tả ảnh](announcement-media:{id})` at the cursor from the manifest item id — no duplicated textarea
   or RAF logic; the composer exposes a `markFocused` flag so an insert before the editor is focused appends
   at the end instead of prepending at position 0.

**P2/P3 BOUNDARY ADJUSTED (the one deviation from the P1 plan).** The original split put inline-preview
wiring in P3. It is **folded into P2** because the manifest is already fetched for the grid thumbnails:
the preview pane now receives `mediaManifest` (BE `id`→renderer `mediaId`, the SAME map the resident
`AnnouncementDetailPage.tsx:55` does), so an inserted INLINE placeholder resolves live after upload/insert.
**P3 is now ONLY the COVER-as-banner render in the admin preview + polish** (the cover entry stays a banner,
never inline — same C2.3a convention as the resident page). Both mutations refetch `['announcements',id]`
(`refetchType:'all'`) so the grid AND the preview's fresh presigned URLs refresh after every upload/delete.

**Scope: FE-only, NO backend/contract change.** Wired the existing C2.2 endpoints (`POST/GET/DELETE
/announcements/{id}/media`); only net-new BE-adjacent change is **3 VN error strings** added to
`getVnErrorMessage` (`ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED`, `ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED`,
`ANNOUNCEMENT_NOT_DRAFT`) — these codes already existed on the BE but had no VN mapping. `docs/API-SPEC.md`
**unchanged.** No body auto-rewrite on delete (a stranded placeholder resolves to nothing — C2.3a unknown-id
behaviour). Notify flags stay hardcoded (`sendPush:true/Email:false/Sms:false`).

**Verification + /code-review (high, workflow-backed).** admin `tsc --noEmit` + `vite build` green; `@gemek/ui`
vitest 32/32 (+3 for the new codes + guard-array extension). Admin has **no vitest harness** (per P1) → no
admin unit tests. The review returned **0 Must-fix**; 5 FE-fixable findings applied pre-commit: (a) cover-toggle
reset [CONFIRMED]; (b) insert-at-0 → append-when-unfocused [CONFIRMED]; (c) `mediaManifest` `useMemo` so a
fresh array each keystroke no longer defeats `MarkdownContent`'s memo [CONFIRMED perf]; (d) HTTP 413
(servlet per-file size, no `error` code) → a specific "Ảnh quá lớn (tối đa 10MB)" message instead of the
generic fallback [CONFIRMED]; (e) extend the exhaustive VN-guard test array with the 3 new codes.

**DEBT logged for CTO (deferred to P3 polish — none blocking).** (1) **No optimistic insert** — a freshly
uploaded image only appears in the grid after the detail refetch completes (brief gap on a slow link); this is
inherent to the CTO-specified refetch-after-upload path. (2) **`atLimit = media.length >= 5`** counts COVER+INLINE
together — this MATCHES the BE total cap (`MAX_MEDIA_PER_ANNOUNCEMENT=5` counts all kinds), so it is correct, not
a bug (a finder flagged it on a false premise). (3) **`AnnouncementMediaItem` vs `AnnouncementMediaManifestEntry`**
differ only by `id` vs `mediaId` — minor DRY; a shared `toMediaManifest` helper is P3 polish. (4) The delete-confirm
dialog **hand-rolls modal markup** instead of `@gemek/ui` `Modal` (no body-scroll-lock) — an established admin
pattern (`AnnouncementsPage.tsx` does the same), not new drift; reuse `Modal` in P3.

**Alternatives rejected.** (a) Per-item "set as cover" toggle on an uploaded image — rejected in favour of the
locked kind-at-upload toggle (simpler, matches C2.2 cover-replace). (b) Auto-insert the image on upload success —
rejected; the CTO ruling is an explicit per-item "Chèn vào bài", and auto-insert + the refetch path would race
(the placeholder would reference an id not yet in the manifest). (c) Keeping inline-preview in P3 — rejected
because the manifest is already in hand for thumbnails, so resolving inline placeholders is free in P2.

---

## 2026-06-25 | C2.3b P1.5 — announcement UPDATE derives target block/floor from scope (close scope-downgrade desync)

**Decision (CTO ruling, locked).** `AnnouncementServiceImpl.updateAnnouncement` now NORMALIZES the target
fields by the incoming/effective `targetScope` (scope = source of truth) instead of the prior
null-means-leave-unchanged binding: ALL → block+floor cleared; BLOCK → block kept/required, floor cleared;
FLOOR → block+floor kept/required. Block/floor are set authoritatively **regardless of what the client
sent**, so even a stale block with scope=ALL gets nulled. `validateScopeConstraints` (already shared with
create) enforces block-required-for-BLOCK/FLOOR + floor-required-for-FLOOR → validation parity. The null
handling for the OTHER fields (title/content/type/sendPush/Email/Sms) is UNCHANGED — only the scope/block/
floor trio is scope-derived. FE also adds a floor upper-bound (≤32767) guard in P1's review pass.

**/code-review follow-ups folded in (P1.5 review):** (1) the SAME scope-derived normalization was extended to
`createAnnouncement` — a stray block/floor sent alongside a less-specific scope is now NOT persisted on create
either (closes the create-side asymmetry the review CONFIRMED; the CTO task explicitly allowed unifying
create+update). (2) The update block resolution was hardened against a regression the first cut introduced:
it now re-fetches the block ONLY when the client supplies a (changed) `targetBlockId`, and otherwise REUSES the
already-attached managed entity — so a content-only or floor-only edit of a BLOCK/FLOOR draft never 404s if
that block row was later deleted (the naive `effectiveBlockId != null` gate would have re-fetched on every edit).
Two tests added for these (content-only edit keeps block without re-fetch; create ALL+stray-block → block null).

**SEVERITY finding (Step-1 investigation, cited HEAD `c4ae28a`).** The stranded target was **harmless
hygiene, NOT a live mis-target.** Both recipient-resolution paths branch STRICTLY on `scope` and read
block/floor ONLY inside the matching branch: resident feed `publishedForResidenciesSpec`
(`AnnouncementServiceImpl.java:217-231` — `scope==ALL` predicate reads no target) and dispatch
`ResidentRepository.findRecipientUserIdsByScopeName` (`:272-274` — `:scope='ALL' OR (BLOCK AND block=…) OR
(FLOOR AND block=… AND floor=…)`). So a scope=ALL record with a stale `targetBlock` resolves as ALL
(everyone) — the stale block is ignored at dispatch + feed. Consequence was cosmetic only (admin list label
rendered "Tất cả - BlockX") + a latent data-integrity smell. Also the edit page is brand-new (P1, never
CTO-smoked) → **no production announcement was ever updated via this path** → nothing was affected. Fixed
anyway per CTO ruling: data integrity + future-proofing against any later code that reads target
unconditionally. **Closes the scope-downgrade desync** (was a P1 /code-review CONFIRMED finding).

**Tests (Mockito unit, RED→GREEN shown).** 4 added to `AnnouncementServiceImplTest`: BLOCK→ALL clears both;
FLOOR→BLOCK clears floor keeps block; ALL→BLOCK sets block; BLOCK with no block → VALIDATION_ERROR. The two
downgrade tests FAIL on the pre-fix code (verified by stashing the service change: 2 failures / 22), PASS
after. Full suite **422/422** (was 418, +4), sequential + order-independent.

**Alternatives rejected.** (a) Fix in BE binding only when client explicitly sends null — fragile, still
null-coupled, doesn't defend against a stale value sent alongside ALL. (b) A shared create+update
"normalize target by scope" helper — cleaner long-term but widens blast radius into create's passing tests
for a gate fix; kept the change tight to update per the CTO "keep tight" allowance. (c) Leave as hygiene-only
(no fix) — rejected by CTO: a record whose stored target contradicts its scope is a latent correctness trap.

---

## 2026-06-25 | C2.3b — admin authoring UX: 3 CTO rulings + FE-only scope + P1/P2/P3 split

**Decision (CTO rulings, locked).** Move admin announcement create/edit out of the modal into dedicated
pages with a 2-column compose|preview layout. Three rulings:
1. **New-draft-id = SAVE-FIRST (option b).** `/announcements/new` shows compose + "Lưu nháp"; saving
   `POST /announcements` creates the draft, then redirects to `/announcements/:id/edit`. Media controls are
   a later phase (P2) — `/new` has no media controls. ("Tạo & đăng" = create then publish, same page.)
2. **Remove the create modal FULLY and ATOMICALLY in P1.** Entry points (the "Tạo thông báo" button +
   a new drafts-only per-row "Sửa") navigate to the pages; the modal code + its state/handlers are deleted
   in the same change. No transitional fallback, no dead code.
3. **Cover selection = kind-at-upload toggle — DEFERRED to P2** (not built in P1).

**Scope: C2.3b is FE-only.** No backend/contract change. The BE `PUT /announcements/{id}` (draft-only,
ADMIN), `POST /{id}/publish`, `POST /announcements`, and the C2.2 media endpoints + draft manifest all
**pre-exist**; P1 only adds the missing FE `useUpdateAnnouncement` hook wired to the existing PUT, the
`useAnnouncement` detail query, and the two pages. `docs/API-SPEC.md` unchanged.

**P1/P2/P3 breakdown.** P1 (this phase) = routing + modal→pages move, no behaviour change, no media:
two pages porting the modal form 1:1 (all fields, validation, cursor-aware `insertMarkdown` ref+RAF
toolbar, two-stage create→publish recovery, 2-col compose|preview), `useUpdateAnnouncement` + drafts-only
"Sửa" entry, modal removed. P2 = media manager (wire C2.2 upload/list/delete) + insert-image dropping
`announcement-media:{id}` at the cursor from the upload response id + cover toggle. P3 = preview pane
consumes the detail `media` manifest (map BE `id`→renderer `mediaId`), refetch-after-upload, COVER banner.

**Reasoning.** Save-first picked because **no orphan-draft cleanup job exists** (no `@Scheduled` for
announcements) and the 4 `@NotBlank/@NotNull` draft fields — so a draft should exist only after a
deliberate save. Modal removed (not kept) to avoid two authoring surfaces drifting. Cursor `insertMarkdown`
reused (no rich-editor swap) to keep the C1/C2.3a XSS bounds and because P2's insert-image needs that exact
mechanism. **Edit "Đăng" saves current edits before publishing** so publish never ships a stale draft
(mirrors the create→publish two-stage pattern; draft survives a publish failure).

**Alternatives rejected.** (a) auto-create empty draft on `/new` entry → **orphans with no janitor** +
dummy values to satisfy required fields → junk drafts visible in the list. (c) client-stage uploads,
attach after first save → a **second (blob) render path** diverging from the single safe-manifest path
C2.3a unified, plus temp-id→real-id rewriting and partial-failure-on-replay complexity. Both rejected for
P1's new-draft-id; (b) is lowest orphan + lowest complexity. **No admin test harness exists** (apps/admin
has no vitest/jsdom/testing-library, build = `tsc && vite build`) → P1 verification is tsc + vite build, no
new unit test (standing up a harness is out-of-scope cost the task explicitly says not to force).

---

## 2026-06-25 | MinIO presign public host — Option B (nginx front + dual MinioClient)

**Decision (CTO ruling).** Make announcement/ticket presigned URLs browser-reachable by fronting MinIO
behind nginx, NOT by publishing raw 9000.
- **Dual `MinioClient`** (`MinioConfig.java`): internal `minioClient` (`@Primary`, byte ops put/delete,
  endpoint `minio:9000`) + presign-only `minioPresignClient` (endpoint `MINIO_PUBLIC_ENDPOINT`, defaults
  to internal when unset). `FileStorageService.presign()` uses the public client (wired via explicit
  `@Qualifier` — two same-type beans + `@Primary` would otherwise inject the internal into both params);
  `upload()`/`delete()` keep the internal client.
- **Region pinned** (`minio.region`, default `us-east-1`, set on both builders): the minio SDK 8.5.9
  presign otherwise issues a GetBucketLocation **network call** to resolve region — which fails for the
  public client (its host is unreachable from the backend container). Pinning makes presign truly offline.
- **nginx** (`nginx.conf`): new `server { listen 8090; location / { proxy_pass http://minio:9000; } }`
  with `proxy_set_header Host $http_host` — the FULL host **including port**, since the presign signs
  `SignedHeaders=host` for `localhost:8090`; `$host` (no port) would mismatch → 403. Dev compose publishes
  `8090:8090`; raw `9000` stays unpublished.
- **Env:** `MINIO_PUBLIC_ENDPOINT=http://localhost:8090` (dev). Prod object delivery via a real
  subdomain/TLS is **[PLANNED]**, not decided here.

**Verified (HTTP, host shell):** detail `media[].url` host = `localhost:8090`; GET that URL → **200**; same
path with a forged `Host: evil:1234` → **403** (signature host-binding intact). Suite 418/418.

**Alternatives rejected.** (a) Publish raw `9000:9000` — exposes MinIO directly, CTO declined. (b) Single
client + nginx/client **host-rewrite** of an already-signed URL — breaks `SignedHeaders=host` → 403; the
host must be the one the URL is *signed* for, so a second presign client is unavoidable.

---

## 2026-06-23 | C2.3a — announcement image render: safe-img rule + placeholder→manifest + fresh-presign limitation

**Decision.** The shared `MarkdownContent` renderer (`@gemek/ui`) re-enables `<img>` but ONLY for the
internal `announcement-media:{id}` placeholder, resolved against a server-minted **media manifest** prop.
- **Safe-img rule:** a live `<img>` is emitted ONLY when the markdown image `src` is
  `announcement-media:{id}` AND that id is present in the manifest; the `src` is then the manifest's
  presigned URL, never the author's string. Any other src — external URL, neutralised `javascript:`/
  `data:`, or raw HTML `<img onerror=…>` — renders nothing (or alt text), never a live img. Only safe
  attributes set (src, escaped alt, `loading="lazy"`); no author-controlled handlers/styles. C1 posture
  unchanged: no rehype-raw, zero `dangerouslySetInnerHTML`, scheme-filtered links (the internal scheme is
  passed through the global urlTransform for images but **stripped on links** so an
  `[x](announcement-media:id)` link is inert). Single shared config — no divergent copy.
- **Placeholder→manifest:** `GET /api/announcements/{id}` returns `media: [{id, kind, url}]`. The renderer
  maps `announcement-media:{id}` → manifest `url`; the `COVER` entry is rendered as a **banner** by the
  page (not inline), inline entries resolve in the body. A placeholder id absent from the manifest
  (deleted/foreign) renders nothing.
- **Access:** manifest URLs are minted via the C2.1 `assertMediaPresignAccess` gate (checked once per
  announcement — all rows share its scope), so an out-of-scope resident gets the text but an empty
  manifest. No loosening of the C2.1 presign, no upload/delete change.

**Reasoning.** Keeping `<img src>` bound to an id-resolved, server-minted, scope-gated URL closes the SSRF
/ tracking-pixel / arbitrary-URL surface that a free `<img>` would reopen, while still rendering legitimate
announcement images. Reusing the C2.1 gate keeps the access rule single-sourced with the feed scope.

**Accepted limitation.** Presigned URLs are minted **fresh per detail request** (10-min expiry, no schema
URL storage). A detail page left open >10 min may show a broken image; **no refresh mechanism** is built
(CTO ruling). Acceptable because images load immediately on render.

**Alternatives rejected.** (a) Allow arbitrary external image URLs — rejected (SSRF/leak/tracking, escapes
the announcement access scope). (b) Embed long-lived URLs / raw object keys in the body or client —
rejected (leaks the key, no expiry). (c) Build a client-side presign-refresh loop — rejected per CTO.

---

## 2026-06-23 | Test-suite / dev-DB-pollution standing issue CLOSED; parallel limitation recorded separately

- **CLOSED — dev-DB-pollution RESOLVED.** Re-verified at the current tree (`reports/test-suite-pollution-verification.md`):
  tests run against a dedicated isolated `gemek_test` @5433 (`application-test.yml`) with per-JVM Flyway
  clean+migrate (`AbstractIntegrationTest.java:108`) and a clean-guard restricted to `/gemek_test` (`:97-99`) —
  dev `gemek` is never touched. Two back-to-back full runs with NO DB reset between, each in a different random
  order, both **379/379 green** → repeat-stable + order-independent, no live pollution. The old
  142-login-401 / AdminSeeder-skip-on-polluted-shared-DB mode is no longer possible (`AdminSeeder.java:75`
  skip-if-`existsByRole(ADMIN)` is safe because the per-JVM clean wipes admin first, forcing a fresh re-seed).
  The standing test-suite "open issue" is removed from the open list.
- **SEPARATE, non-blocking — parallel execution NOT safe (NOT pollution).** A parallel run yields 0 failures /
  10 errors, all the identical `IllegalStateException: Cannot start new transaction without ending existing
  transaction` — Spring `@Transactional` test-transaction nesting across threads in one JVM/context. This is a
  test-framework limitation, not a data-pollution bug and not conflated with the resolved issue. Enabling
  parallel is an OPTIONAL future CTO call (forked JVMs with per-fork DBs, or dropping `@Transactional` rollback
  on the parallelized classes); until then, sequential execution stands as the reliable safety net.

## 2026-06-23 | Move-out (item d) confirmed COMPLETE & multi-residency-safe

Move-out is fully implemented and verified (NOT net-new): `POST /api/residents/{id}/move-out` is residency-scoped
(`{id}` = `residents.id` via `findById`, ends ONE residency not the user), with conditional account deactivation
only when no other active residency remains, per-row primary-contact clear, a MOVED_OUT `resident_history` event
(actor = acting admin), and derived occupancy. Admin UI present (`ResidentsPage` per-row "Kết thúc cư trú" + confirm
dialog) and lists per-residency (no dedupe-by-user → both apartments of a multi-residency user are independently
move-out-able). Item (d) is CLOSED. Evidence: `reports/move-out-investigation.md`. **Sole remaining
residency-lifecycle tail = amenity real attribution rule `[PLANNED]`** (temporary primary-or-latest; deferred LAST
per CTO, not needed yet).

## 2026-06-23 | Residency-lifecycle P0–P3 COMPLETE & CTO-smoked end-to-end — closing note + open items

**Closed:** Residency-lifecycle P0–P3 complete & CTO-smoked end-to-end. Concurrent multi-residency is creatable
via the place-resident flow (existing active phone + `confirmReuse=true` → 2nd active residency in a different
apartment; relaxed index `uq_residents_active_user (user_id, apartment_id) WHERE move_out_date IS NULL` permits
it, same `(user, apartment)` still blocked). Reactivate on return is **enabled-only** (`[hoãn]` force-reset — a
returning user logs in with old credentials; revisit later if deemed a risk). State verified against code/DB this
turn (live index from `pg_indexes`; lookup + branching endpoints in `ResidentController`/`ResidentServiceImpl`),
not from memory. Phase commits: P0 `2c2f8e6`/`7d378d7`, P1 `dbd4848`, P2 `bc85522`, P3 `f464397`+`c907a0e`.

**OPEN items (carry forward — a future session should pick these up):**
- **(a) Amenity real attribution rule — `[PLANNED]`, NOT decided.** Booking/listBookings still use the temporary
  deterministic *primary-or-latest* residency. Markers live at
  `backend/.../module/amenity/AmenityServiceImpl.java:284` + `:298`
  (`// [PLANNED] multi-residency: temporary primary-or-latest selection; real attribution rule pending CTO ruling`);
  API-SPEC carries the matching `[PLANNED — multi-residency attribution]` note. The real "which apartment is a
  booking charged to" rule needs a CTO ruling.
- **(b) Move-out admin UI item (d) — PRESENT (done).** Confirmed this turn:
  `frontend/apps/admin/src/api/hooks.ts:130` (`useMoveOutResident` → `POST /api/residents/{id}/move-out`) wired
  into `frontend/apps/admin/src/pages/ResidentsPage.tsx` ("Kết thúc cư trú" button `:319` → `openMoveOut` →
  `mutateAsync` `:106`, with date-picker + notes confirm dialog). BE conditional account-deactivation on move-out
  also landed earlier. Item (d) is NOT an open gap; recorded here only to retire the question.
- **(c) Deprecated `findActiveByUserId` cleanup — PENDING.** The `@Deprecated` `Optional`-returning query remains
  at `backend/.../module/resident/repository/ResidentRepository.java:101` with **no production callers** (verified
  by grep this turn). Retained since P1 pending a separate cleanup pass; safe to delete when convenient.
- **(d) FE `any`-type debt.** Pre-existing `any` usage on list-item handlers (e.g. ticket/resident pages) is
  unrelated to residency correctness and out of this chain's scope; noted only so it isn't mistaken for residency
  debt. No action required for residency-lifecycle.

---

## 2026-06-23 | Residency lifecycle — P3 place-resident flow (AS IMPLEMENTED)

**See:** `reports/p3-place-resident.md`, PROGRESS "P3 DONE", API-SPEC §Residents (lookup + POST /residents).
Implements the move-in / return / add-concurrent flow per the CTO rulings — one smart endpoint branching on
phone (Phương án 1), ADMIN-only, two-step UX with server-side re-resolution.

- **One smart endpoint (Phương án 1).** `POST /api/residents` branches server-side on phone: NEW (provision
  user+residency), REUSE (existing user → add `residents` row + reactivate if disabled), or
  `ALREADY_ACTIVE_IN_APARTMENT`. The admin's mental action is "place this phone into this apartment"; the server
  decides new vs returning vs add-concurrent. The old `PHONE_ALREADY_EXISTS` hard block is REMOVED — it was the
  dead-end this phase exists to kill (investigation §C).
- **Lookup endpoint = GET (not POST).** `GET /api/residents/lookup?phone=&apartmentId=` (ADMIN, read-only).
  RESTful for a read; consistent with the existing `?search=` param endpoints. Phone-in-querystring is acceptable
  because the endpoint is ADMIN-gated (same exposure as `GET /users?search=<phone>`). PII discipline: returns
  ONLY status + display name + active-apartment ids — never phone/email/dob/password/audit.
- **ALREADY_HERE precedence.** When the lookup is given a target `apartmentId` and the user already actively
  resides in it, `ALREADY_HERE` is returned ahead of `ACTIVE_ELSEWHERE`. Phone-only lookup never returns
  ALREADY_HERE (it's apartment-specific); the place endpoint validates it at place-time regardless (server never
  trusts the lookup).
- **Server never trusts step 1 (IDOR-safe).** The place endpoint re-resolves the phone via `findByPhone` and
  accepts NO client-supplied userId. On reuse, identity (name, dob, email, password) is taken from the existing
  user and NEVER overwritten by request values — proven by the integration test that smuggles a different
  name/password and asserts the old ones survive (login with old password works, smuggled fails).
- **REUSE_CONFIRMATION_REQUIRED contract.** `confirmReuse != true` on a known phone (not active in target) →
  409 `REUSE_CONFIRMATION_REQUIRED`, creating nothing. Carried by `ReuseConfirmationRequiredException extends
  AppException` + a dedicated `@ExceptionHandler` that emits the standard error body PLUS a `matched` object (the
  lookup DTO) so the FE can render the confirm popup. `ALREADY_ACTIVE_IN_APARTMENT` uses a plain `AppException`
  (no extra body). Chosen over a 200-with-discriminated-union body to stay consistent with the project's
  exception-based error architecture.
- **Same (user, apartment) handled explicitly, not via the index.** An explicit
  `existsActiveByUserIdAndApartmentId` pre-check surfaces the duplicate as a clean 409
  `ALREADY_ACTIVE_IN_APARTMENT` instead of letting the relaxed unique index throw a
  `DataIntegrityViolationException`. The index remains the backstop (and catches true concurrent races via the
  existing handler → 409). Different-apartment reuse is permitted → concurrent multi-residency.
- **Alternatives considered:** (a) two separate endpoints (create-new vs add-existing) — rejected, CTO ruled one
  smart endpoint; (b) client passes the matched userId from step 1 — rejected (IDOR); (c) 200-with-status union
  response for the confirmation case — rejected (inconsistent with the exception-based error path).

## 2026-06-23 | Residency P3 — reactivate is enabled-only ([hoãn] force-password-reset)

- **Decision:** On the REUSE/return path, reactivating a disabled account sets `user.active = true` ONLY. Role is
  NOT changed, password is NOT reset/cleared, and NO force-password-reset flag is added.
- **[hoãn]:** reactivate currently only re-enables the account; returning users log in with their OLD credentials.
  Revisit a force-password-reset (or admin-set temporary password) later if deemed a security risk — a returning
  resident's old password may have been shared/rotated during their absence. Deferred per CTO ruling; not a P3
  concern.
- **Reasoning:** minimal, reversible, and matches the move-out side (which only flips `active=false`). Anything
  more (credential rotation) is a separate product/security decision, not implied by "let them back in".

## 2026-06-23 | Residency P3 — conditional NEW-branch validation moved into the service

- **Decision:** Removed `@NotBlank`/`@Pattern`/`@NotNull` from `CreateResidentRequest.fullName` / `password` /
  `dateOfBirth`. The service enforces them (presence + password complexity, via `PASSWORD_PATTERN`) ONLY in the
  NEW branch (`provisionNewUser`), throwing `VALIDATION_ERROR` (400) — preserving the existing weak-password
  contract (`createResident_weakPassword_400`). Always-required fields (phone, apartmentId, type, moveInDate)
  keep their bean constraints.
- **Reasoning:** these three fields are required only when the phone is new; the reuse branch must accept a body
  WITHOUT them (identity is reused). Bean validation cannot branch on a DB phone lookup, so the conditional
  requirement has to live in the service. This is a deliberate, logged exception to the standing "every DTO that
  accepts a password carries `@Pattern`" rule — the complexity check is relocated, not dropped.
- **Alternatives considered:** (a) keep the constraints + have the FE send a dummy password on reuse — rejected
  (sending an ignored password is a security smell and would still need a valid-looking value); (b) a second DTO
  / endpoint — rejected (CTO ruled one endpoint); (c) class-level cross-field `@AssertTrue` — rejected (still
  can't see the DB to know the branch).

---

## 2026-06-22 | (d) follow-up — Move-out conditional user deactivation: rule + deactivation mechanism

- **Decision (rule):** On move-out, deactivate the linked user account (`user.active = false`) ONLY when the user has no remaining active residency — checked via `residentRepository.existsActiveByUserId(userId)` AFTER `moveOutDate` is set (so the just-moved-out residency no longer counts). Implemented as the general "no other active residency" check even though the model is effectively 1-active-residency today, so it stays correct if multi-residency arises.
- **Reasoning:** Resident→User is `@ManyToOne` (a user can hold multiple resident rows; only one active per the `uq_residents_active_user` partial unique index). The login identity is the user; residency is what we gate on. A user still living in another apartment must keep their login → conditional, not unconditional.
- **Decision (mechanism):** Flip `user.active` directly on the entity (mirrors `createResident`'s `user.setActive(true)`, `ResidentServiceImpl.java:157`) instead of calling `UserServiceImpl.deactivateUser`.
- **Reasoning:** `deactivateUser(id, requestUserId)` has a `SELF_OPERATION_NOT_ALLOWED` guard that throws when `id == requestUserId` — meant for the admin UsersPage flow; inside move-out it would wrongly abort a valid move-out if an actor ever moved out their own residency. Its only behavior is `setActive(false)` (auth is stateless JWT — no token-revocation side-effect exists to preserve). Direct entity set is the established in-service pattern and avoids the inapplicable guard; net effect is identical to the admin deactivate.
- **Atomicity:** runs inside the existing `@Transactional moveOut` — deactivation failure propagates and rolls the move-out back (move_out_date not committed).
- **Alternatives considered:** (a) unconditional deactivate on move-out — rejected (breaks multi-residency users); (b) reuse `deactivateUser` — rejected for the self-op guard + cross-module coupling with no side-effect benefit; (c) a second FE API call — rejected (not atomic, per task).

## 2026-06-22 | (d) Resident move-out UI — surface placement + Option B date

- **Decision:** The "Kết thúc cư trú" action + moved-out state live on `ResidentsPage` (the `/residents` list, per-row) — NOT a new resident detail page/route.
- **Reasoning:** No dedicated admin Resident DETAIL page/route exists; `/residents` (ADMIN-gated) is the only resident admin surface. Adding a per-row status column (badge / action button) + a confirm modal reuses the page's existing load/refetch (`useResidents` + `invalidateQueries(['residents'])`) with zero new routing. Not a fundamentally different architecture → no BLOCKER.
- **Date handling (CTO Option B):** moveOutDate is a `VNDatePicker` defaulted to today (`toISODateLocal(new Date())`), editable, + optional notes. The original "no picker, BE stamps now()" plan was dropped because `POST /residents/{id}/move-out` requires `moveOutDate @NotNull` in the body (`MoveOutRequest.java:26`) and the service persists the client date (`ResidentServiceImpl.java:265`) — BE does NOT stamp server-side. Payload is a pure ISO `yyyy-mm-dd` (no time) → BE `LocalDate`.
- **Alternatives considered:** (a) build a new ResidentDetailPage/route — rejected as scope expansion; the list is the established resident surface and the moved-out state reads from the existing `ResidentResponse.moveOutDate`. (b) no-picker + FE auto-fill today (Option A) — CTO chose B (editable date) instead.

## 2026-06-18 | (e) self-profile endpoint — DTO shape & validation choices

- **Decision:** `PUT /api/auth/me/profile` DTO `UpdateOwnProfileRequest` requires `fullName` and `phone` (`@NotBlank`), `email` optional. role/isActive/password deliberately ABSENT.
- **Reasoning:** phone is the NOT-NULL unique login identifier — a self-update must keep a valid phone, so `@NotBlank` (mirrors `CreateUserRequest`, stricter than admin `UpdateUserRequest` which allows blank). role/isActive omitted so the record cannot bind them = structural privilege-escalation guard (no client-supplied id either → no IDOR). Uniqueness pre-checks exclude the caller's own row so an unchanged phone/email is not a false conflict.
- **Alternatives considered:** (a) reuse admin `UpdateUserRequest` — rejected, it carries role/isActive (escalation surface) and a target id. (b) PATCH-style partial nulls — rejected, FE sends the full small form; full-replace of 3 fields is simpler and matches `GET /me` shape.

## 2026-06-09 | Cluster 1 form-feedback patch (5 forms)

**Decision:** Login (both apps): error → `getVnErrorMessage(code)`, success → navigate (no toast). Change-password / Book amenity / Rate ticket: success → `toast.success('Vietnamese message')`; error → `getVnErrorMessage(code)`. Inline English success removed from ProfilePage (was `pwSuccess` state + static string).

**Why:** Login navigate-on-success is sufficient UX signal; toast there would be redundant. The other 3 are non-navigation mutations where the user stays on the same page — a toast is the only clear success signal.

**Toast API (locked standard):** `toast.success(message: string)` / `toast.error(message: string)`. Do NOT call `toast(...)` directly — it is an object, not a function. All future clusters must use `.success()` / `.error()` form.

**Interceptor URL guard (locked standard):** Both `apiClient` interceptors skip the 401 refresh+retry for `/auth/login` and `/auth/refresh` — these return 401 as a business signal, not a token-expiry signal. Add any future auth-only endpoint to this guard if it returns 401 for business logic.

**WRONG_CURRENT_PASSWORD (422):** Wrong current password in change-password flow uses `HttpStatus.UNPROCESSABLE_ENTITY` (422) not `UNAUTHORIZED` (401). 422 bypasses the 401 interceptor → error reaches component immediately. `INVALID_CREDENTIALS` (401) is reserved for login only.

**skipSuccessToast pattern (SUPERSEDED — see 2026-06-09 toast-position fix):** The "component path unreliable" diagnosis was wrong — the actual bug was Tailwind purging Toast classes (resident tailwind.config missing packages/ui/src scan). Component-level `toast.success()` is reliable once tailwind scans the package. See canonical pattern below.

---

## 2026-06-09 | Change-password toast + password-policy error (round 2 fixes)

**Decision A — Success toast via meta.successMessage:** `useChangePassword` uses `meta: { successMessage: 'Đổi mật khẩu thành công.' }`. Still valid but reason was wrong — singleton is fine; this just avoids the component needing to import toast. Either path works.

**Decision B — PASSWORD_POLICY_VIOLATION (422) for weak new password:** `@Pattern` removed from `ChangePasswordRequest.newPassword` — Spring's `MethodArgumentNotValidException` maps to generic `VALIDATION_ERROR`. Domain validation (password complexity) moved to `AuthServiceImpl.changePassword()` → throws `PASSWORD_POLICY_VIOLATION` (422). FE maps to "Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt." Pattern: any domain-specific validation that needs its own user-facing VN message must have its own `ErrorCode` enum entry; never rely on `VALIDATION_ERROR` for domain rules.

---

## 2026-06-09 | Toast positioning + canonical pattern (locked)

**Root cause:** Resident `tailwind.config.js` did not include `packages/ui/src` in content scan → all `Toast.tsx` classes purged in production build → toast renders as unstyled white block in document flow. Admin config already had the scan; only resident was missing it.

**Mobile fix (c518623 — partially wrong):** `Toast.tsx` replaced inline style with Tailwind classes. Tailwind purge was real and fixed. However `md:left-auto md:right-4` still anchored to viewport right edge.

**Viewport-anchor fix (c4b3179 — correct):** Root cause was `position:fixed` anchored to viewport right edge. Resident layout is `max-w-md mx-auto` (448px centered) — on wide desktop, `right-4` puts toast far outside the app column. Fix: `left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm` — viewport-centered, always over the column. Prior `md:left-auto md:right-4` removed.

**Tailwind scan rule (locked):** Every app that imports `@gemek/ui` components MUST include `../../packages/ui/src/**/*.{ts,tsx}` in its `tailwind.config.js` content array. Failure = all UI-package classes purged.

**Toast singleton (confirmed):** ONE `listeners[]` instance. `packages/ui/src/components/Toast.tsx` resolves to the same absolute path from all import sites (App.tsx, component files, mutationToast.ts). No module duplication.

**Canonical success-toast pattern (locked for remaining clusters):**
- Component-level `toast.success('VN message')` — use when message depends on response data, or form has its own catch block already importing toast.
- `meta: { successMessage: 'VN message' }` — use for simple fixed messages where the hook can define it; cleaner if component doesn't need toast for other reasons.
- Both are equivalent. `skipSuccessToast: true` only needed when BOTH paths would fire (component calls toast AND meta.successMessage is set → two toasts).

---

## 2026-06-10 | i18n architecture — plain-TS dictionary, shared + per-app, enum maps separate

**Decision:** Internationalize both React apps using a plain TypeScript dictionary approach. No `react-i18next` or any new dependency.

**Architecture (locked):**
1. **Shared dictionary** in `@gemek/ui` (`packages/ui/src/lib/vi.ts`) — cross-app strings (Hủy, Lưu, Sửa, Đang tải..., Trước, Sau, Thao tác, Trạng thái, generic empty-state pattern). Exported `t(key, params?)` helper with `{variable}` interpolation (e.g. `t('greeting', { name })` → `"Xin chào, An"`). Interpolation required from day one — inventory has `{name}`, `{N}`, `{unitNumber}`.
2. **Per-app dictionary** (`src/i18n/vi.ts` in each app) — app-specific strings that import/extend shared strings. Admin and resident each get their own file.
3. **Enum display-maps** kept **entirely separate** from UI-text dictionaries. These map BE enum keys → VN display labels (e.g. `CAR` → `"Ô tô"`). `value=` attributes ALWAYS stay the raw BE enum key; only the visible label is translated. Location: `src/i18n/enums.ts` per app (placeholder; populated in a later dedicated step).
4. **TEMP_HIDDEN_DEFERRED pages** (amenities, bookings, parking nav) — translate these too when they are un-deferred; do not skip them.

**Pilot scope:** resident `Layout.tsx` + `HomePage.tsx` only. Demonstrates shared vs. per-app string split. Await CTO approval before rollout.

**Rollout order (after pilot approval):** resident remainder → enum display-maps → admin (~3–4 page clusters matching reports/i18n-inventory.md).

**Why:** react-i18next adds build complexity (loader, provider, namespace files) for a single-language app that only needs EN→VN. A plain-TS dictionary is zero-dep, type-safe, tree-shakeable, and fully auditable. Enum maps are separated because their keys come from BE domain model (not UI copy) and are used in `value=` attributes — conflating them with copy strings would break form submissions.

**Alternatives:** react-i18next (rejected — overkill for single target language); inline string replacement per file (rejected — no central auditability, no reuse across apps).

---

## 2026-06-10 | Defer Module 10 notification dispatch to dedicated sprint

**Decision:** Do NOT implement announcement-to-notification dispatch now. Record as deferred tech debt.

**Why:** `AnnouncementServiceImpl.publishAnnouncement()` was intentionally stubbed ("Module 10"). Wiring it during form-feedback cluster work would be scope creep. CTO must approve scope before implementation — three interconnected breaks (dispatch, bell unread count, announcement body render + per-user isRead) make it a proper feature sprint, not a one-liner.

**Full trace:** `reports/publish-notification-trace.md`

**Publish toast corrected (2065821):** Changed `usePublishAnnouncement` `meta.successMessage` from "Đã đăng thông báo tới cư dân." to "Đã đăng thông báo." — must not claim residents were notified until dispatch is implemented.

---

## 2026-06-09 | Form-feedback standard + distinct dup-code finding

**Decision:** All forms in both apps follow one standard: validation/server errors → VN inline message below field; success → toast. No mixed patterns.

**Finding:** BE already emits `PHONE_ALREADY_EXISTS` vs `EMAIL_ALREADY_EXISTS` as distinct codes (confirmed in `ErrorCode.java`, `ResidentServiceImpl.java`, `UserServiceImpl.java`). No BE changes needed for dup-key distinction — only FE mapping is missing.

**Why:** FE was hardcoding "Email đã được sử dụng." for all 409 dup errors regardless of which field caused it. The BE already provides the correct signal; FE must surface it as per-field inline error.

**2026-06-09 addendum:** `ResidentServiceImpl` was throwing generic `CONFLICT` for email-dup (one-line fix, e66b86e). Root cause: service-layer email guard was added independently from the phone guard and used the wrong error code. Both paths now symmetric: phone-dup → `PHONE_ALREADY_EXISTS`, email-dup → `EMAIL_ALREADY_EXISTS`. Test updated (902ff48) — old test asserted `CONFLICT` (was testing the bug, not the intent).

**How to apply:** FE catch block reads `err?.response?.data?.error` (the BE error code string). Map known codes to fixed VN strings via a shared util; unknown codes and non-409 errors fall back to "Có lỗi xảy ra, vui lòng thử lại." — never echo `err?.response?.data?.message` (leaks raw server text, may expose phone/email, may be English). Set field-level error (`setPhoneError` / `setEmailError`) where the field state exists; otherwise set form-level `setFormError`.

---

## 2026-06-09 | Form-feedback foundation — shared getVnErrorMessage util

**Decision:** Single `getVnErrorMessage(errorCode?: string): string` pure function in `@gemek/ui/src/lib/errorMessages.ts`, exported from `@gemek/ui`. Both apps import this; no duplicated code in individual forms.

**Mapping:** All 16 BE ErrorCode values mapped to Vietnamese. Generic codes (CONFLICT, NOT_FOUND, VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR) get broad but correct VN sentences. Specific codes (PHONE_ALREADY_EXISTS, EMAIL_ALREADY_EXISTS, INVALID_CREDENTIALS, etc.) get precise VN sentences. Unknown/undefined → fallback "Có lỗi xảy ra, vui lòng thử lại."

**BE gap list:** 7 CONFLICT throw sites flagged in `reports/error-code-audit.md` where a more specific code would improve UX (highest priority: license-plate dup in VehicleServiceImpl — currently FE can only show generic CONFLICT message). These are NOT fixed now; logged for a future BE patch turn.

**Why:** Centralizing the mapping prevents drift (each form choosing different VN phrasing) and ensures that raw English server messages can never leak to the UI through this path.

**Alternatives:** Inline the mapping per form — rejected (maintenance burden, easy to forget fallback rule).

---

## 2026-06-09 | Cluster-1 lessons locked for clusters 2–5

**Lesson 1 — Success toast path:** Use `meta: { successMessage: 'VN message' }` in the TanStack Query mutation hook. MutationCache `onSuccess` fires `toast.success(meta.successMessage)` automatically. Component-level `toast.success()` also works (singleton, same listeners[]) but requires importing toast in the component. `meta.successMessage` is preferred for fixed messages. `skipSuccessToast: true` only needed when both paths fire simultaneously.

**Lesson 2 — Toast API shape:** `toast` is an object with `.success(msg)` and `.error(msg)` methods. Calling `toast({...})` throws. All future clusters must use `toast.success(msg)` / `toast.error(msg)`.

**Lesson 3 — Toast positioning (locked, do not revert):** Toast container `fixed left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm`. Viewport-centered. Prior `fixed right-4` anchored to full-viewport right edge — outside resident's `max-w-md mx-auto` column on desktop. Resolved in c4b3179. Do not add responsive right-anchor back.

**Login rule:** Login success = navigate only. No toast on auth endpoints. All other mutations: success → toast via MutationCache or component.

**Change-password specifics:** `useChangePassword` hook: `meta: { skipErrorToast: true, successMessage: 'Đổi mật khẩu thành công.' }`. Errors caught inline: `getVnErrorMessage(err?.response?.data?.error)` — maps `WRONG_CURRENT_PASSWORD` and `PASSWORD_POLICY_VIOLATION` to specific VN strings.

**Auth state confirmed stable:** Phone-as-login complete. Change-password hash integrity verified in `reports/change-pw-integrity.md` — no corrupting path exists in current code; earlier corruption non-reproducible.

---

## 2026-06-08 | Dup-phone 500 fix

- Root cause: Docker container ran stale code (pre-step-5 ResidentServiceImpl had no existsByPhone guard); dup phone hit DB constraint → DataIntegrityViolationException → catch-all → 500.
- Fix 1: Added DataIntegrityViolationException → 409 CONFLICT handler in GlobalExceptionHandler (defense-in-depth for race conditions and future constraints).
- Fix 2: Rebuilt backend container to deploy step-5 phone guard in ResidentServiceImpl.
- Fix 3: ResidentsPage 409 inline message now uses `err?.response?.data?.message` (was hardcoded wrong "Email đã được sử dụng.").
- Note: `useCreateResident` has `skipErrorToast: true` — no global toast; errors surface inline via setFormError. Intentional for now; remove skipErrorToast if toast desired.

---

## 2026-06-08 | Demo seed script

- Data: 3 blocks, 10 apartments (4/3/3 per block), 30 residents (3 per apt: 1 OWNER + 2 TENANT), 5 staff (2 ADMIN + 3 TECHNICIAN).
- Password `Demo@1234`, BCrypt strength=12; hash embedded as literal (no `$` env-var interpolation).
- Phones: staff `0901100001–0901100005`, residents `0901200001–0901200030` — all canonical `^0[3-9]\d{8}$`, no collision with default admin `0900000000`.
- Amenities, amenity_bookings, parking intentionally excluded (features hidden/deferred).
- Script replaces prior `scripts/seed-demo-local.sql` (previous version had 30 apts + vehicles + contractors + tickets + bookings — too heavy).

---

## 2026-06-08 | Phone-as-login COMPLETE — canonical decisions

1. Login identifier = phone (was email). Email is informational only — NOT login, NOT required.
2. Canonical stored form: `0xxxxxxxxx` (leading 0, 10 digits, `^0[3-9]\d{8}$`; VN mobile only).
3. All input formats normalized on BE only via `PhoneUtils.normalize()` — FE does no normalization.
4. Email: UNIQUE constraint kept; NOT NULL dropped (nullable-unique; multiple NULLs allowed in Postgres).
5. DB reset locally acceptable — no data migration required (V12 migration handles schema).
6. `CreateResidentRequest.email`: @NotBlank removed (commit 4237cba — mixed into docs commit; flagged for awareness).

---

## 2026-06-08 | API-SPEC v2.1 aligned to phone-as-login as-built (step 8)

POST /api/auth/login: request phone (was email), response user.phone (was email), normalization note added. POST /api/users: phone required, email optional. POST /api/residents: phone required + normalized, dateOfBirth required, email optional; error codes updated (PHONE_ALREADY_EXISTS added). Also fixed CreateResidentRequest.email: removed @NotBlank (email is optional per V12 schema; @Email format validation kept).

---

## 2026-06-08 | Resident creation path: phone normalization + uniqueness (step 5)

`ResidentServiceImpl.createResident()` built User directly, bypassing `UserServiceImpl.createUser()`. Added `PhoneUtils.normalize()` + `existsByPhone` check (→ PHONE_ALREADY_EXISTS 409) before persist. Email null-guard added (email is now optional). Consistent with UserServiceImpl order: normalize → phone-unique → email-unique → persist. Dup-phone was previously a 500 DB constraint violation; now 409.

---

## 2026-06-08 | FE login switched from email to phone (step 6)

Both apps (admin + resident): `AuthUser.email→phone`, `login(email)→login(phone)`, POST body `{email}→{phone}`. `LoginPage.tsx` both apps: label "Số điện thoại", `type="tel"`, loose VN phone regex UX gate only — BE normalizes and validates definitively. `ProfilePage.tsx` (resident): `user?.email→user?.phone` (minimal build-fix, full audit step 7). `ResidentsPage.tsx` `r.user?.email` left as-is (typed `any`, no TS error; display audit step 7). Builds: admin ✅ resident ✅.

---

## 2026-06-05 | Stable id tie-breaker added to all paginated list sorts — makes ordering deterministic (was causing intermittent test failures + unstable pagination across page boundaries)

---

## 2026-06-05 | POST /api/residents — provisions new user + resident in one transaction; old assign-existing-user (userId) flow removed (breaking change)

---

## 2026-06-05 | users.date_of_birth — nullable DATE column via V11 migration
Added nullable `date_of_birth DATE` to users table (V11 migration). Exposed in UserResponse, UserDetailResponse, ResidentResponse.UserRef. No create/update flow yet — additive read-only this turn.

---

## 2026-06-05 | GET /api/residents — search param + apartment.block in response

**Decision:** Added optional `search` query param (Criteria API LIKE on user.fullName/email, same null-safe pattern as UserRepository fix). Added `apartment.block.name` to `ResidentResponse.ApartmentRef` and `ResidentMapper`. Fetch joins for user/apartment/block added to data query (not count) to avoid N+1.

**Why:** Admin vehicle form needs a resident dropdown with server-side search (>100 residents exceed client-side cap). Apartment block needed to derive `apartmentId` after resident selection. Backward-compatible — only adds fields and a new optional param.

---

## 2026-06-05 | SearchableSelect async server-search mode | loadOptions opt-in prop

**Decision:** Added optional `loadOptions?: (query: string) => Promise<SearchableOption[]>` to SearchableSelect. When provided, debounces 300ms calls to the function instead of filtering client-side. Selected label stored in state, persists when not in current search results. Static mode (no prop) unchanged; user dropdown on create-resident uses it.

**Why:** User list has 202 entries; backend max-page-size cap of 100 means client-side filtering can't surface 101–202. Server-search via `GET /api/users?search=<q>&size=20` reaches any user. Apartment and block dropdowns remain client-side (lists small enough).

**Alternatives considered:** Raise backend maxPageSize to 500 (risky, large payloads); `async` boolean flag (forces caller to wire fetch externally). Chosen approach keeps component self-contained.

---

## 2026-06-05 | UserRepository — replace @Query findAllWithFilters with JpaSpecificationExecutor

**Decision:** Removed the `@Query`-based `findAllWithFilters` from `UserRepository`; `UserRepository` now extends `JpaSpecificationExecutor<User>`. `UserServiceImpl.listUsers()` builds a `Specification<User>` programmatically using Criteria API, only adding the LIKE predicate when `search` is non-null.

**Why:** Hibernate 6 + PostgreSQL JDBC driver binds all `String` named parameters in JPQL `LOWER(CONCAT('%', :param, '%'))` expressions as `bytea` regardless of value (null or non-null). PostgreSQL resolves ALL parameter types at query-planning time, so IS NULL short-circuit cannot prevent the `lower(bytea)` error. Multiple JPQL workarounds attempted failed: `COALESCE(:search, '')` (still bytea), `COALESCE(:search, column)` (type conflict error), `cast(:search as string)` (caused `could not determine data type of $1` for the IS NULL check of an unrelated enum param). Specifications use the JPA Criteria API which binds `String` parameters as `varchar` via `cb.like(cb.lower(root.get("fullName")), pattern)`.

**Alternatives considered:** `cast(:search as string)` in JPQL (broken — shifts error to other params); `COALESCE(:search, column)` (broken in tests — `COALESCE types bytea and varchar cannot be matched`); native query (requires projection interface and separate count query, not worth the complexity). Specifications are the Spring-recommended approach for dynamic optional filters.

---

## 2026-06-04 | API-SPEC corrected to match as-built backend — amenity approve/reject and parking routes

Amenity: original spec defined two separate endpoints (`PUT /amenity-bookings/{id}/approve` with `{ notes }` and `PUT /amenity-bookings/{id}/reject` with `{ reason }`). The backend was implemented with a single unified endpoint (`PUT /amenity-bookings/{id}/approve`) accepting `{ status: BookingStatus, rejectionReason }`. The `/reject` endpoint was never built. API-SPEC updated to document the as-built contract. FE aligned to BE.

Parking: original spec defined `POST /parking/assignments` (create) and `PUT /parking/assignments/{id}/end` (unassign). The backend was implemented as `POST /parking/slots/{id}/assign` and `POST /parking/slots/{id}/unassign` (slot-centric routing; `id` in path is slot UUID in both cases). `endDate` on create not supported — assignments are open-ended. API-SPEC updated; FE hooks fixed to call real routes.

## 2026-06-04 | Announcement DTO field rename: `scope`→`targetScope` in CreateAnnouncementRequest, UpdateAnnouncementRequest, AnnouncementResponse — to match API-SPEC.md contract. UpdateAnnouncementRequest renamed in the same pass for consistency (spec uses `targetScope` for both create and update endpoints). Entity field `Announcement.scope` and DB column `target_scope` unchanged.

---

## Architecture Decisions

### 2026-05-29 | Modular Monolith over Microservices
**Decision:** Single deployable JAR, feature-package organized internally.
**Reasoning:** 1000 apartments, small team. Distributed services overhead unjustified. Cross-module operations (assign ticket → update contractor rating) benefit from single-transaction semantics.
**Alternatives:** Microservices, domain-driven separate services.

### 2026-05-29 | Spring Boot 3.3 + Java 21 backend
**Decision:** Spring Boot 3.3 with Java 21 LTS.
**Reasoning:** VTIT team standard. Java 21 virtual threads improve I/O efficiency for notification-heavy workloads. Mature security, data, web layers.
**Alternatives:** Node.js/Fastify, Python/FastAPI.

### 2026-05-29 | PostgreSQL 15 single instance
**Decision:** Single PostgreSQL instance, all tables in public schema.
**Reasoning:** Data volume <10 GB for years of operation. Cross-module joins trivial. JSONB for audit log. Native ENUM types for ticket category/status.
**Alternatives:** MySQL 8, MariaDB.

### 2026-05-29 | UUID primary keys throughout
**Decision:** All PKs use gen_random_uuid().
**Reasoning:** Sequential integers allow enumeration attacks. No meaningful performance cost at this scale.
**Alternatives:** BIGSERIAL auto-increment.

### 2026-05-29 | Ticket module replaces maintenance module
**Decision:** Single `tickets` table with `category` ENUM (MAINTENANCE_REPAIR, COMPLAINT, ADMINISTRATIVE, SUGGESTION_FEEDBACK, OTHER) replaces the narrower `maintenance_requests`.
**Reasoning:** Business requirement updated: residents can submit any type of request. Unified table with category-based routing is simpler than separate tables per type.
**Alternatives:** Separate tables per request type, polymorphic inheritance.

### 2026-05-29 | Contractor assignment restricted to MAINTENANCE_REPAIR at DB level
**Decision:** CHECK constraint: `assigned_to_contractor_id IS NULL OR category = 'MAINTENANCE_REPAIR'`. Service layer also validates before DB touch.
**Reasoning:** Business rule: only physical repair work goes to contractors. DB-level enforcement prevents bypass even through direct DB access.
**Alternatives:** Service-layer-only validation (weaker).

### 2026-05-29 | SLA per category, computed at INSERT
**Decision:** `tickets.sla_deadline = created_at + category.sla_hours`. SUGGESTION_FEEDBACK has no SLA (NULL).
**Reasoning:** Scheduler runs simple indexed range query. Partial index on `(sla_deadline) WHERE status NOT IN ('DONE','CANCELLED')` keeps hourly check fast.
**Alternatives:** Dynamic SLA computation on query.

### 2026-05-29 | MinIO for file storage (object keys in DB)
**Decision:** Self-hosted MinIO. DB stores object keys, not full URLs. Backend issues presigned GET URLs with 1-hour expiry.
**Reasoning:** No cloud vendor lock-in. Docker-native. URL never expires in DB; bucket changes only require config update.
**Alternatives:** AWS S3, Cloudflare R2.

### 2026-05-29 | JWT stateless auth with Redis blocklist
**Decision:** 15-min access tokens + 7-day refresh tokens in Redis. Logout adds JTI to Redis blocklist with TTL.
**Reasoning:** Stateless verification; Redis escape hatch for revocation.
**Alternatives:** Keycloak, Spring Session.

### 2026-05-29 | Two separate React apps in pnpm workspace
**Decision:** apps/admin (desktop-first) + apps/resident (mobile-first) sharing packages/ui.
**Reasoning:** Different UX paradigms. Clean separation. Shared components avoid duplication.
**Alternatives:** Single SPA with role-based routing.

### 2026-05-29 | SMS gateway: pluggable no-op interface
**Decision:** SmsGateway interface; real provider injected via @ConditionalOnProperty.
**Reasoning:** Vietnamese telco SMS APIs vary; abstracting avoids forcing a vendor choice.
**Alternatives:** Hardcode a specific provider.

### 2026-05-29 | Notification delivery: fire-and-forget
**Decision:** FCM/SMTP/SMS failures logged at WARN, do not roll back business transaction. In-app record always created.
**Reasoning:** External delivery failures must not block core operations.
**Alternatives:** Outbox pattern (overkill at this scale).

---

## Backend Decisions

### 2026-05-29 | V1 migration creates all ENUM types upfront
**Decision:** V1__create_enums_and_users.sql creates all PostgreSQL ENUM types (not just user_role), even though only the users table is created in this migration.
**Reasoning:** Flyway applies migrations in order; later module migrations reference these ENUMs. Creating them all in V1 avoids cross-migration dependencies and eliminates the risk of a future migration failing because its ENUM type does not yet exist.
**Alternatives:** Create each ENUM in the migration where it is first used. Rejected because migration ordering becomes a fragile dependency.

### 2026-05-29 | BCrypt strength 12 for password encoder
**Decision:** BCryptPasswordEncoder configured with cost factor 12.
**Reasoning:** ~250ms per hash on modern hardware — strong enough against brute force, acceptable latency for a login endpoint that is rate-limited to 10/min/IP anyway.
**Alternatives:** Cost 10 (Spring default, faster but weaker). Argon2 (overkill complexity for this scale).

### 2026-05-29 | Redis KEYS pattern scan for refresh token deletion on logout
**Decision:** On logout, `redisTemplate.keys("refresh:{userId}:*")` is used to delete all refresh tokens for the user.
**Reasoning:** Invalidates all devices simultaneously on logout, which is the correct security behaviour. The KEYS command is acceptable because Redis holds only a small number of refresh token entries per user and the production deployment is a single Redis instance.
**Alternatives:** Track refresh JTIs in a Redis set per user (more correct at scale, unnecessary complexity now).

### 2026-05-29 | X-Forwarded-For header honoured for IP rate limiting
**Decision:** AuthServiceImpl reads X-Forwarded-For (first IP in chain) when present, falling back to RemoteAddr.
**Reasoning:** Requests arrive via Nginx reverse proxy; RemoteAddr would always be the Nginx container IP, making per-IP rate limiting useless. Trusting X-Forwarded-For from Nginx is safe in this single-proxy setup.
**Alternatives:** Configure Nginx to set X-Real-IP instead (equivalent, chose standard header).

### 2026-05-29 | AuditLogAspect implemented as a stub
**Decision:** AuditLogAspect intercepts @Auditable methods but only logs at DEBUG level — no DB write yet.
**Reasoning:** The audit_logs table is not created in this module's migration. The annotation and aspect are wired now so future modules annotate freely; the full implementation will be added in the reporting module when audit_logs migration is introduced.
**Alternatives:** Skip @Auditable entirely for now. Rejected because it would require a refactor pass across all services later.

### 2026-05-29 | No MapStruct mapper for Ticket module — service builds DTOs manually
**Decision:** `TicketServiceImpl` builds `TicketSummaryResponse` and `TicketDetailResponse` directly in private helper methods instead of via a MapStruct mapper.
**Reasoning:** `TicketDetailResponse.photos` requires `FileStorageService.presign()` to generate presigned URLs for each photo. Injecting a Spring service into a MapStruct mapper (via `uses=`) introduces lifecycle coupling and complicates testability. The manual mapping pattern keeps all business logic in the service layer and is consistent with `ApartmentServiceImpl` which also builds nested DTOs by hand.
**Alternatives:** MapStruct with `@Mapper(componentModel="spring", uses={FileStorageService.class})`. Rejected — the photo URL generation is a side-effectful call (HTTP to MinIO), not a pure field mapping.

### 2026-05-29 | FileStorageService mocked in TicketControllerTest
**Decision:** `@MockBean FileStorageService` in `TicketControllerTest`; `presign()` returns a fixed URL stub.
**Reasoning:** The 8 required tests cover ticket lifecycle rules, not photo storage. Starting a MinIO Testcontainer for non-photo tests adds 10–15 s of startup time per CI run with no benefit. All photo-path tests can be added as dedicated integration tests when needed.
**Alternatives:** Testcontainers MinIO (minio/minio image). Deferred to a dedicated photo upload test suite.

### 2026-05-29 | ContractorRepository uses Jakarta @Transactional not Spring @Transactional
**Decision:** `ContractorRepository.recalculateRating` is annotated with `jakarta.transaction.Transactional` because it is a Spring Data repository interface method — Spring Data requires the Jakarta variant at the interface level for `@Modifying` queries when no encompassing Spring transaction exists.
**Alternatives:** Rely on the calling service's `@Transactional` context. The explicit annotation ensures safety if the method is called outside a transaction.

### 2026-05-29 | NotificationController follows direct-DTO pattern, not ApiResponse wrapper
**Decision:** `NotificationController` returns `ResponseEntity<PageResponse<T>>` and `ResponseEntity<DomainDto>` directly, without wrapping in `ApiResponse<T>`.
**Reasoning:** Every existing controller in the codebase (`ParkingController`, `AnnouncementController`, etc.) returns domain DTOs or `PageResponse` directly. Introducing `ApiResponse` on this single module would break response-shape consistency across the API.
**Alternatives:** Wrap in `ApiResponse<T>` as stated in the module spec. Rejected because it contradicts the established codebase contract.

### 2026-05-29 | MaintenanceScheduleRunner.checkOverdueSchedules annotated @Transactional(readOnly=true)
**Decision:** The scheduled method carries `@Transactional(readOnly=true)` so the JPA session remains open while the loop traverses `schedule.getContract().getCreatedBy()` lazy associations.
**Reasoning:** Without an open session, accessing lazy-loaded associations on detached entities throws `LazyInitializationException`. A read-only transaction is the lightest-weight fix and avoids adding an eager fetch or a new repository query.
**Alternatives:** Add a JOIN FETCH to `findOverdue`; load contracts eagerly. Rejected to keep the existing query untouched.

### 2026-05-29 | AuditLogAspect resolves entity ID via reflection on getId()
**Decision:** After the primary method completes, the aspect calls `result.getClass().getMethod("getId").invoke(result)` to extract a UUID entity ID. Failures are silently swallowed.
**Reasoning:** The `@Auditable` annotation has no `entityId` field, and adding one would require annotating every call site. Reflection on a stable `getId()` convention covers the common case with zero annotation changes.
**Alternatives:** Add `entityId` attribute to `@Auditable`; pass entity ID explicitly at each call site. Deferred — can be added when precise entity-ID tracking is required.

### 2026-05-29 | Report queries added to existing repositories, no new repository files
**Decision:** Report-specific queries (`countByStatus`, `getDashboardTicketKpis`, `getTicketBreakdown`, `getResidentDemographics`, etc.) were added as new methods on existing module repositories rather than creating a separate reporting repository layer.
**Reasoning:** All data lives in existing tables. A separate `ReportRepository` would require duplicating entity knowledge or using raw `EntityManager`, adding complexity with no benefit. The convention used throughout the codebase is to extend existing repositories with @Query methods.
**Alternatives:** JPA Criteria API in service; separate `ReportRepository` with `EntityManager`. Both rejected as overkill at this scale.

### 2026-05-29 | Dashboard KPIs computed in real-time, no caching
**Decision:** All dashboard aggregations run as live DB queries on every request.
**Reasoning:** Building cache invalidation across five modules (apartments, tickets, amenities, contracts, residents) would add significant complexity. At 1000 apartments the queries are fast with existing indexes. Cache can be added later (Redis, @Cacheable) when load testing identifies it as necessary.
**Alternatives:** Redis cache with TTL; materialized view refreshed on schedule. Deferred.

### 2026-05-29 | Ticket report groupBy=assignee joins users table for display name
**Decision:** The `getTicketBreakdown` native query LEFT JOINs `users` on `assigned_to_user_id` and uses `COALESCE(u.full_name, 'Unassigned')` as the label.
**Reasoning:** Assignee name is more readable in a report than a UUID. Unassigned tickets are grouped under a single "Unassigned" label rather than excluded.
**Alternatives:** Return assignee UUID and resolve name client-side. Rejected — adds round-trips and complicates the frontend.

## Deployment Decisions

### 2026-06-03 | Admin portal port 80, resident portal port 81 (single nginx container)
**Decision:** One nginx container builds both React apps (multi-stage Dockerfile) and serves them on separate ports: admin on 80, resident on 81.
**Reasoning:** Both apps share the same API backend and nginx config; running one container avoids duplication. Different ports cleanly separate the two audiences without requiring different domains or subdirectory base URLs (no Vite `base` change needed).
**Alternatives:** Two separate nginx containers; nginx subdirectory routing (requires `base: '/admin/'` in vite configs). Both rejected to minimise config changes.

### 2026-06-03 | MinIO console port 9001 exposed on host for first-run bucket setup
**Decision:** `ports: ["9001:9001"]` in docker-compose.yml so operators can create the `gemek` bucket via the web console on first deploy.
**Reasoning:** No `minio/mc` init container to avoid pulling an extra image. Console access is sufficient for a single-instance local/staging deployment.
**Alternatives:** `minio-init` sidecar running `mc mb`. Deferred — adds image pull requirement.

---

## Frontend Decisions

### 2026-05-29 | Access token in Zustand memory, refresh token in localStorage
**Decision:** Access token stored in Zustand (in-memory only). Refresh token persisted to `localStorage` key `gemek_refresh`.
**Reasoning:** Access token in memory is more secure (not accessible to XSS via document.cookie). Refresh token in localStorage survives page reload. Full HttpOnly cookie migration deferred — requires backend Set-Cookie change on /auth/refresh.
**Alternatives:** Both in HttpOnly cookies (most secure, tracked as post-G4 hardening item).

### 2026-05-29 | window.__gemekAuthState bridge removed — Zustand imported directly
**Decision:** Removed `window.__gemekAuthState` and `window.__gemekSetToken` globals. Axios client imports Zustand store directly.
**Reasoning:** Security scanner flagged globals as token exposure surface. Direct import works cleanly — no circular import issue in practice.
**Alternatives:** Keep window bridge for circular import safety. Rejected after confirming no circular import exists.

---

## Testing / Infrastructure Decisions

### 2026-05-29 | Testcontainers docker.host uses docker_cli named pipe
**Decision:** `~/.testcontainers.properties` sets `docker.host=npipe:////./pipe/docker_cli`.
**Reasoning:** Docker Desktop on Windows returns HTTP 400 from `docker_engine` and `dockerDesktopLinuxEngine` pipes. The 400 response body contains label `com.docker.desktop.address=npipe://\\.\pipe\docker_cli` — this is Docker Desktop's redirect hint to the actual API socket.
**Alternatives:** TCP on localhost:2375 (requires Docker Desktop setting to expose daemon — security risk); WSL2 socket (requires additional config).

### 2026-05-29 | AuthServiceTest uses LENIENT Mockito strictness
**Decision:** `@MockitoSettings(strictness = Strictness.LENIENT)` added to `AuthServiceTest`.
**Reasoning:** `@BeforeEach` stubs `httpRequest.getRemoteAddr()` and `httpRequest.getHeader("X-Forwarded-For")` which are needed by login tests but not by logout/refreshToken tests. Strict mode throws `UnnecessaryStubbingException`. LENIENT is correct here — the stubs are shared setup, not test-specific noise.
**Alternatives:** Remove stubs from `@BeforeEach`, add per-test. Rejected — too much duplication across 4 login tests.

---

### 2026-06-04 | Application-level admin seeding replaces Flyway hash placeholder
**Decision:** `AdminSeeder` (ApplicationRunner) creates the admin user on first boot by hashing a plaintext `ADMIN_PASSWORD` env var with BCrypt-12. V2 migration no longer contains an admin INSERT. `spring.flyway.placeholders.ADMIN_PASSWORD_HASH` removed from application.yml.
**Why:** Docker Compose interpolates `$` characters in env_file values. A BCrypt hash (`$2b$12$<salt>...`) contains `$` followed by valid variable-name characters; compose substitutes them with blank, truncating the hash to 27 chars. Flyway seeds the corrupted hash; login always fails. Plaintext env var has no `$` corruption risk.
**How to apply:** Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `.env`. On first boot with an empty DB, AdminSeeder hashes the plaintext and inserts the admin. Idempotent — skips if any ADMIN role user exists. Fail-loud — throws `IllegalStateException` if no admin exists and `ADMIN_PASSWORD` is blank.
**Migration checksum note:** V2 was rewritten (INSERT removed). This is safe ONLY for environments that wipe and re-migrate from scratch (`docker compose down -v`). Any environment that has already applied the original V2 will fail Flyway checksum validation and must use a new migration (V11+) to correct the admin hash instead.

### 2026-06-04 | GET /api/amenity-bookings allows TECHNICIAN and BOARD_MEMBER (spec says ADMIN+RESIDENT only)
**Decision:** Kept TECHNICIAN and BOARD_MEMBER in the @PreAuthorize allowlist for GET /api/amenity-bookings even though API-SPEC.md lists only ADMIN and RESIDENT.
**Why:** These staff roles have an operational need to view bookings (approvals, facility scheduling). Narrowing them would be a breaking change with no security benefit — they already have ADMIN-level read access on other booking endpoints.
**How to apply:** Do not remove TECHNICIAN/BOARD_MEMBER without a deliberate product decision. The spec discrepancy is a documentation gap, not a security issue.

### 2026-06-04 | RESIDENT scoping for GET /api/amenity-bookings is server-side only (IDOR prevention)
**Decision:** When role=RESIDENT, the service ignores the client-supplied `residentId` param and forces-scopes results to the caller's own active resident record (looked up via `residentRepository.findActiveByUserId`).
**Why:** Trusting the client residentId would be an IDOR — a resident could pass another resident's UUID and see their bookings. Server-side derivation from the JWT principal is the only safe approach.
**How to apply:** Any future endpoint that lists user-owned resources must follow the same pattern: derive identity from the principal, never from a client-supplied ID.

### 2026-06-04 | GET /api/residents/me — identity from JWT principal only
**Decision:** Added self-scoped endpoint returning the caller's active resident record. No userId in path or query — the principal UUID is derived from the JWT via `@AuthenticationPrincipal UserPrincipal`. Returns 404 (NOT_FOUND) if user has no active residency.
**Why:** Resident apartment derivation via ticket[0].apartment broke for residents with zero tickets. A dedicated identity endpoint is reliable and eliminates the IDOR surface.
**How to apply:** Any endpoint returning user-owned resources must derive identity from the principal, never from a client-supplied ID.

## 2026-06-04 | GET /api/tickets `status` filter widened to multi-value repeated param
**Decision:** Changed `@RequestParam TicketStatus status` (single enum) to `@RequestParam List<TicketStatus> status` (repeated param). Query uses `IN` when list non-empty, no restriction otherwise. Added `MethodArgumentTypeMismatchException` handler in `GlobalExceptionHandler` returning 400 VALIDATION_ERROR.
**Why:** Resident home screen needs to show "open" tickets (NEW + ASSIGNED + IN_PROGRESS) in a single call. Sending a comma-joined string to a single-enum param caused `IllegalArgumentException` → 500. Repeated params (`?status=NEW&status=ASSIGNED`) bind natively via Spring's `List<Enum>` binding with clean 400 on bad input.
**Alternatives considered:** Comma-separated list with a custom `ConversionService` converter — rejected because repeated params are standard HTTP, require no custom code, and Spring's binding already handles them correctly.

## 2026-06-05 | GET /api/vehicles `search` param — Criteria API, OR on licensePlate/brand/model
**Decision:** Added `search` as case-insensitive substring OR across `licensePlate` (NOT NULL), `brand`, `model` (nullable — coalesced to "" before `lower()`). Criteria API only; no JPQL LIKE. Additive to existing `apartmentId` filter.
**Why:** ParkingPage vehicleId dropdown needs server-search to find vehicles without knowing exact UUID. Criteria API avoids Hibernate-6 null→bytea bug on nullable LIKE parameters. `brand`/`model` included so partial make/model strings resolve vehicles without knowing the plate.
**Alternatives considered:** JPQL with `(:s IS NULL OR LOWER(v.licensePlate) LIKE ...)` — rejected (Hibernate-6 bytea bug on null params, proven failure on this project).

## 2026-06-08 | Password complexity enforced on CreateResidentRequest (@Pattern from CreateUserRequest)
**Decision:** `CreateResidentRequest.password` gains the same `@Pattern` regex as `CreateUserRequest` — min 8 chars, upper+lower+digit+special. Commits: 697edfd (fix), c704a04 (test).
**Why:** POST /api/residents creates a new user; weak password accepted there while rejected on POST /api/users is an inconsistency and security gap.
**How to apply:** Any DTO that accepts a user password must carry the same `@Pattern` constraint.

---

## 2026-06-08 | Add-resident form: inline user creation + generate-password button; phone + dateOfBirth required
**Decision:** Admin add-resident form replaced the obsolete "select existing user" flow (SearchableSelect on users) with inline new-user creation fields. Generate-password button added. `dateOfBirth` field added. `phone` and `dateOfBirth` are now required on both the form (FE validation) and `CreateResidentRequest` (`@NotBlank`/`@NotNull`). Commits: 448bc15 (form), fa47149 (dob + validation), c1e3789 (BE required), 0061e4c (tests).
**Why:** POST /api/residents was changed (cc42a6c session) to provision a new user+resident transactionally — the old "assign existing user" UI was already broken. phone is now a login identifier (phone-as-login initiative); dateOfBirth is needed for resident profiles.
**How to apply:** Do not add an "assign existing user" path back without a deliberate product decision.

---

## 2026-06-08 | Global success-toast bug: Tailwind purging layout classes from @gemek/ui
**Decision:** Root cause of success toasts not appearing was Tailwind CSS content scanning missing `packages/ui/src`. Classes `top-4`, `right-4`, `z-[200]` were purged. Fixed by adding `'../../packages/ui/src/**/*.{ts,tsx}'` to `content` in both apps' `tailwind.config.js`. Commit: 3dc1d6b.
**Why:** TanStack MutationCache `onSuccess` was wiring correctly (d905b56); the toast component rendered but was positioned off-screen due to purged positioning classes.
**How to apply:** Any new shared component in `packages/ui/src` with Tailwind classes is automatically scanned after this fix. If a new package is added under `packages/`, add it to both tailwind configs.

---

## 2026-06-08 | JAVA_HOME prerequisite documented in NEW-SESSION.md
**Decision:** Documented that running Maven tests requires `JAVA_HOME` set to JDK 21 (Eclipse Adoptium path on this machine) and Maven path to IntelliJ's bundled Maven. Commit: 2d49936.
**Why:** `mvn` is not in PATH; IntelliJ's bundled Maven at `C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd` must be invoked directly with `$env:JAVA_HOME` set.
**How to apply:** See `NEW-SESSION.md` for the exact PowerShell snippet before running any Maven commands.

---

## 2026-06-08 | PHONE-AS-LOGIN — CTO decisions (IN PROGRESS)
**Decision:** Login identifier switches from email to phone number. Full implementation plan in `reports/phone-username-survey.md` section D.

CTO decisions (fixed requirements):
1. Login identifier = phone number; email is informational only, NOT used for login, NOT required.
2. Canonical stored form: `0xxxxxxxxx` (leading 0, 10 digits, VN mobile `^0[3-9]\d{8}$`; landlines rejected).
3. Accepted input formats: `0962464748`, `+84962464748`, `+840962464748`, `84962464748`, `840962464748` — all normalize to `0962464748`. Strip `+84`/`84`, ensure leading `0`. `+840...`/`840...` (redundant 0 after country code) handled: after stripping `84`, remainder already starts with `0` — do not double.
4. Normalization MUST happen on backend only via `PhoneUtils.normalize()` (single shared util). FE may validate for UX only.
5. DB reset locally acceptable — no data migration required.
6. email: keep `UNIQUE` constraint but drop `NOT NULL` (nullable-unique allows multiple NULLs in Postgres).

Steps done: step 1 PhoneUtils + tests (4b3f020), step 2 V12 migration (41b90ca). Steps 3–9 remaining — see PROGRESS.md.

**WARNING:** Do NOT boot the app until step 3 (entity) + step 4 (AdminSeeder) both land. V12 makes `phone NOT NULL`; old AdminSeeder still hardcodes phone — it would fail or insert un-normalized value.

---

## 2026-06-08 | Phone-as-login steps 3–4: login identity now phone-keyed end-to-end in BE
**Decision:** Steps 3 (auth/user module) and 4 (AdminSeeder) complete. Login identifier is now phone throughout the backend.

Step 3 changes (3e59bbc feat + 1ccce1b test):
- `LoginRequest`: `email` → `phone` (`@NotBlank`; real validation via `PhoneUtils.normalize()` in service).
- `AuthServiceImpl.login()`: normalizes phone via `PhoneUtils.normalize()` → `findByPhone(normalized)`.
- `UserPrincipal`: `phone` field; `getUsername()` returns phone; `SecurityConfig.UserDetailsService` updated to `findByPhone`.
- `JwtTokenProvider`: `CLAIM_EMAIL` → `CLAIM_PHONE`; embed `principal.getPhone()` in access token (informational claim; filter still uses `sub` UUID).
- `LoginResponse.UserSummary`: `email` → `phone` field.
- `CreateUserRequest`: `phone` now `@NotBlank` (required); `email` now optional (nullable, `@Email` format-check if provided).
- `UserServiceImpl.createUser()`: `existsByPhone` as primary uniqueness guard (409 → `PHONE_ALREADY_EXISTS`); optional `existsByEmail` guard if email provided; `PhoneUtils.normalize()` before persist.
- `ErrorCode`: added `PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT)`.
- `User` entity: `email` `nullable=true`; `phone` `nullable=false, unique=true` (matches V12 migration).

Step 4 changes (e1e2d14 feat + bb4fe47 test):
- `AdminSeeder`: `adminPhone` injected via `${app.admin.phone:0900000000}`; `PhoneUtils.normalize()` applied before `admin.setPhone()`. Non-canonical env values (e.g. `+84900000000`) are silently corrected; invalid values fail loud at startup.

**Why:** Centralize normalization at PhoneUtils; every write path normalizes before DB insert or lookup, ensuring the `uq_users_phone` UNIQUE constraint (V12) is satisfied regardless of input format.
**How to apply:** Any future endpoint that writes a phone field must call `PhoneUtils.normalize()` before persistence. The `ResidentServiceImpl` (step 5) is next.

---

## 2026-06-10 | #20 Mark Announcement Read — intentional silent (no success/error toast)

**Decision:** `useMarkAnnouncementRead` keeps `meta: { skipSuccessToast: true }` and no `skipErrorToast`. No component-level error handling added. A comment was added to `AnnouncementsPage.tsx` at the call site to document intent.

**Why:** Read-marking is a best-effort background UX action — it happens on tap/click as a side effect of viewing. Showing a "marked as read" toast would be intrusive noise. If it silently fails (network error), the visual unread indicator remains and the user can re-tap. The global error toast (from MutationCache) is acceptable as a rare edge-case signal; no inline try/catch needed.

**How to apply:** Any future "background UX" mutation (e.g. auto-save draft, implicit tracking) should follow this pattern: fire-and-forget, no success toast, global error toast only, comment at call site.

---

## 2026-06-10 | i18n terminology: user-facing "Ticket" = "Phản ánh" (CTO decision, both apps)

**Decision:** Every visible occurrence of "Ticket" translates to "Phản ánh" — both apps, all current and future i18n work. DISPLAY TEXT ONLY: route paths (`/tickets`), variable names, object keys, enum values, file names, and API fields stay unchanged.

**Why:** "Phản ánh" is the natural Vietnamese term residents use for reporting building issues; "Yêu cầu" (used briefly in the pilot) is generic. CTO fixed the term globally to prevent per-page drift.

**How to apply:** New translations use "phản ánh" for ticket nouns (e.g. 'Phản ánh của tôi', 'Tạo phản ánh', 'Chưa có phản ánh nào'). Pre-existing already-VN strings saying "yêu cầu" (e.g. MyTicketsPage 'Gửi yêu cầu' / 'Loại yêu cầu', TicketDetailPage 'Không thể tải yêu cầu hỗ trợ.') were NOT touched this pass — flagged for a later terminology-sweep step with CTO sign-off.

Also: viShared empty-state refined into two keys — `common.emptyYet` ('Chưa có {item} nào', nothing exists yet) vs `common.emptyFound` ('Không tìm thấy {item}', no results after search/filter). Old generic `common.empty` removed (was unused outside tests).

---

## 2026-06-10 | i18n terminology: Announcements = "Tin tức", notification bell = "Thông báo" (CTO decision, both apps)

**Decision:** The announcements feature is "Tin tức" everywhere (nav, home section, page title); the notification bell/panel is "Thông báo". One word per concept — never swap.

**Why:** Cluster 2 initially titled AnnouncementsPage 'Thông báo', colliding with the notification panel word while nav/home said 'Tin tức' — two words for one concept, one word for two concepts.

**How to apply:** Admin app translation must use "Tin tức" for AnnouncementsPage/nav and "Thông báo" only for the notification bell/panel. Enum-label maps for announcement types are separate.

Also: enum display-label maps built in `@gemek/ui` (`packages/ui/src/lib/enumLabels.ts`) — 7 groups (ApartmentStatus, TicketStatus, TicketPriority, ContractorSpecialty, VehicleType, ParkingSlotStatus, ActiveStatus) + `labelFor(enumType, key)` with raw-key fallback. DISPLAY ONLY — `<option value=>`, filters, comparisons keep raw BE enum keys. NOT yet wired into any page; wiring happens per-page during admin (and a later resident cleanup pass).

---

## 2026-06-11 | i18n terminology: block = "Tòa" (CTO decision, both apps)

**Decision:** The building-block concept displays as "Tòa" everywhere (labels, headers, placeholders, validation text). Variable/field names (`blockId`, `block.name`) and API fields stay unchanged.

**Why:** A2 introduced 'Tòa' for headers while older VN strings said "block" ('Chọn block...', 'Vui lòng chọn block.') — mixed wording for one concept.

**How to apply:** Swept 2026-06-11 (commit 9b2de7b): ApartmentsPage placeholder+validation, AnnouncementsPage label+option+validation. Grep for display "block" in admin src returns 0. Future i18n work uses "Tòa".

Also 2026-06-11: TicketDetail (admin) update-status select switched from hardcoded VN ('Hoàn thành') to labelFor('TicketStatus') — DONE now displays 'Hoàn tất' per the locked TicketStatus map.

---

## 2026-06-11 | Date display = dd/mm/yyyy via formatVNDate/formatVNDateTime; local-time render; native date inputs unchanged

**Decision:** All FE date displays go through `@gemek/ui` `formatVNDate()` ('dd/mm/yyyy', zero-padded) / `formatVNDateTime()` ('dd/mm/yyyy HH:mm', 24h). Built manually from `getDate()/getMonth()+1/getFullYear()` + local-time getters — NOT `toLocaleDateString('vi-VN')` (unpadded, engine-variant). Invalid/null → `''` (callers keep their own '—' fallbacks).

**Timezone (deliberate, not a bug):** BE sends ISO (UTC for datetimes); helpers render with LOCAL getters, so users see browser-local time (UTC+7 for VN users). Do not "fix" by switching to UTC getters.

**KIND B limitation (accepted):** the 6 native `<input type="date">` render per the user's OS locale — JS cannot change their display format. Left as-is; replacing with masked inputs or a picker lib = future CTO decision.

**KIND C untouched:** ISO wire strings (payloads, query params, input value/min) unchanged — required by API contract + HTML spec. Full inventory: `reports/date-format-diagnosis.md`.

---

## 2026-06-11 | Module 10 P2: in-app dispatch atomic inside publish TX; publish→409 via CAS; JDBC batch inserts

**Decision (CTO-approved, design §C Option 1 + §D D-1 + CAS):** `publishAnnouncement()` creates in-app notification rows INSIDE the publish transaction — a publish either fully happens (feed-visible AND rows exist) or rolls back. Recipients from P1 `findRecipientUserIds` (single scope-rule source). Rows built with `getReferenceById` (no per-recipient SELECT) + one `saveAll` batch. The 2026-05-29 "fire-and-forget" decision governs external FCM/SMTP/SMS only ("in-app record always created") — external channels remain stubbed (INFO log) for a future sprint, where they MUST be after-commit/async.

**Publish contract change:** already-published publish now returns **409 CONFLICT** (was idempotent-200) — aligned to API-SPEC. Sole guard = atomic CAS `UPDATE … SET published_at = :now WHERE id = :id AND published_at IS NULL` (`AnnouncementRepository.publishIfDraft`); row-count 0 → 409. Same CAS kills the read-then-write race → no duplicate dispatch on double-click/concurrent publish (notifications table has no unique constraint, so this is the only protection).

**Config:** `hibernate.jdbc.batch_size: 50` + `order_inserts: true` added — without it `saveAll` of an ALL-scope dispatch (~1–2k rows @ 1000 apartments) is one INSERT round-trip per row.

**Notification body (§G#9):** short VN string `"Có thông báo mới: " + title` — full content read on announcement page, keeps table small.

---

## 2026-06-11 | Module 10 P4: per-user isRead on AnnouncementResponse — batched read-state query; toResponse default-false overload

**What:** `AnnouncementResponse` gains `@JsonProperty("isRead") boolean isRead` (naming mirrors `NotificationResponse`). List endpoints compute it with ONE query per page (`AnnouncementReadRepository.findReadAnnouncementIds(userId, pageIds)` → in-memory `Set` membership) instead of per-row `exists()` — avoids 20-queries-per-page N+1 at near-zero extra code. Detail endpoint uses the single `existsByAnnouncementIdAndUserId` (degenerate 1-row case).

**toResponse signature:** kept `toResponse(Announcement)` as an overload delegating to `toResponse(Announcement, boolean)` with `isRead=false`. Mutation paths (create/update/publish) use the false-default: a draft can never be read (`markRead` rejects drafts) and a just-published announcement cannot have a read row for the caller within the same TX. Alternatives considered: threading principalId into every toResponse call site (extra exists() queries for values provably false) — rejected.

**Admin list path:** also computes real isRead for the requesting admin (markRead has no role gate) — keeps one rule, no role branch in mapping.

---

## 2026-06-11 | N3 design approved (CTO rulings on reports/n3-event-notifications-design.md §G) + P1 enum migration

**CTO rulings (G1–G8):**
- **G1** — v1 cuts approved: booking events, vehicle-registered, amenity-deactivation notice, move-out notice all OUT of N3 v1.
- **G2** — SLA warning threshold: fixed **2 hours** before `slaDeadline`, all categories.
- **G3** — `is_public` is **immutable after create** (no later toggle in v1).
- **G4** — old assignee's subscription row is **retained** on reassignment (worked the ticket, keeps thread context).
- **G5** — TICKET_CREATED and SLA escalations (warning + breach) go to **active ADMINs only** (no BOARD_MEMBER).
- **G6** — ContractExpiryScheduler daily re-notify bug fixed **in-scope** at P6 (same sent-marker pattern).
- **G7** — dedicated **`TICKET_RATING_REQUESTED`** enum value for the DONE rating prompt (supersedes the C5 reuse-TICKET_STATUS_CHANGED proposal) → V13 adds 4 values, not 3.
- **G8** — public-ticket redaction rule approved as proposed (§E): submitter identity, unit number, photos, rating comment hidden; **photos stay hidden until F-05 presign hardening lands**.

**Terminology rule (binding for P3+):** all BE-generated Vietnamese notification bodies must use the locked FE enum-label terms — ticket status DONE = «Hoàn tất», never «Hoàn thành».

**P1 implementation note:** V13 contains ONLY `ALTER TYPE notification_type ADD VALUE` statements (PG: added values unusable in the defining transaction — no seed rows/DDL may share the file). Java constants inserted into the ticket group of `NotificationType` (string-mapped enum — Java order need not match PG enum order). Round-trip test (`NotificationTypeRoundTripTest`, parameterized over the 4 values, flush + context-clear + reload) proves the `NAMED_ENUM` mapping accepts them.

**P3 note (2026-06-11):** `TicketStatusLabels` (BE) mirrors the locked FE map `frontend/packages/ui/src/lib/enumLabels.ts` TicketStatus section VERBATIM — any future label change must update both files together. C3 recipients = thread snapshot taken BEFORE the assignee subscribes (assignee gets C2 only, never C2+C3). De-flake side-fix shipped with P3 (separate commit d90f98c): `AmenityControllerTest.listBookings_adminSeesAllBookings` asserted page membership in an UNSORTED list on the shared dev DB (209+ committed bookings → page-100 lottery); now asserts via per-amenity filtered calls — deterministic, intent (admin sees other residents' bookings) preserved.

---

## CTO Overrides
_(record when CTO overrides agent decision)_

### 2026-06-03 | SEC-20 remains deferred post-security-audit
**Decision:** SEC-20 (INFO — refresh token in localStorage) stays deferred after the improvement/security audit.
**Reasoning:** Full remediation requires a backend Set-Cookie change on /auth/login and /auth/refresh, which is a post-G4 hardening sprint item. The prior architectural decision (2026-05-29) to store the refresh token in localStorage was made knowingly, with HttpOnly cookie migration tracked explicitly.
**How to apply:** Do not attempt SEC-20 remediation until the CTO schedules the hardening sprint. The open-items section of G4-testing.html documents the required changes.
- GET /api/blocks now returns PageResponse (data/page/size/total/totalPages); search via Criteria API (case-insensitive name substring); default size=10, sort=name asc; max size=200. FE callers already read .data — no FE changes.

## N3 P5 — presign deliberately not widened for public tickets (2026-06-11)

**What:** `assertPresignAccess` no longer delegates to `enforceReadAccess`; it uses a dedicated `enforcePhotoAccess` that keeps the strict pre-P5 household/staff rule. A public ticket grants read access (redacted) but NO photo/presign access.
**Why:** photos can show the inside of a home, and F-05 (presign IDOR hardening) is still open — widening the presign surface before F-05 is forbidden (G8). Reusing the widened read rule would have widened presign automatically.
**Also decided:** list `visibility` param defaults to "mine" (= exact pre-P5 resident scoping) so the existing FE keeps working unchanged; "community" is opt-in for the P7 tab. Resident list rows outside the caller's household are redacted with the same G8 rule as the detail (beyond the plan's letter — otherwise the list would leak submitter name + unit number that the redacted detail hides). Assignee identities also hidden in redacted views for symmetry with hidden changedBy staff names.

## N3 P6 — SLA scheduler implementation choices (2026-06-12)

**What:** `TicketSlaScheduler` runs the overdue scan FIRST, and the warning scan query is lower-bounded with `slaDeadline >= :now` (design §D only specified `< now()+2h` + null marker).
**Why:** the §D both-match edge (already overdue at first sight → send ONLY the breach, set BOTH markers) is enforced structurally: the overdue branch sets both markers, and the lower bound makes the warning query unable to match an overdue ticket even before the in-TX flush — no reliance on Hibernate auto-flush ordering. Both scans share one `now` snapshot, so no ticket can fall between the two predicates within a run.
**Also decided:** whole-run single `@Transactional` (per-batch TX, implementer's call per task brief) — markers flush atomically with notification inserts; a crash rolls back the entire scan and the next run redoes it. Status exclusion uses JPQL enum literals (`NOT IN ('DONE','CANCELLED')`) not enum params — avoids the Hibernate 6.5 NAMED_ENUM param-anchoring issue hit in P1 (precedent: `ContractRepository` `c.status = 'ACTIVE'`). ShedLock intentionally absent (single-container deployment, documented in class Javadoc, same assumption as the two sibling schedulers).

**G6 fix note:** `ContractRepository.findExpiringBetween` modified IN PLACE (`+ expiryNotifiedAt IS NULL`) rather than adding a new method — the scheduler is its only caller (grep-verified); Javadoc updated to state the once-only semantics. Marker set only AFTER a successful `createNotification` inside the now-`@Transactional` run; the existing per-contract try/catch is kept (failure skips the marker → retried next day). +07 test technique: expected deadline string computed with literal `ZoneOffset.ofHours(7)` (VN has no DST) and asserted ≠ the UTC rendering — catches UTC cast artifacts independently of the impl's `ZoneId Asia/Ho_Chi_Minh` and of the JVM default TZ (`-Duser.timezone=Asia/Ho_Chi_Minh` would mask a `LocalDateTime`-based bug).

## N3 P7 — viewer-flag semantics (2026-06-12)

**What:** `isFollowing` counts ONLY FOLLOWER subscription rows — a creator (CREATOR row) viewing their own public ticket gets `isFollowing=false`.
**Why:** the flag drives the «Theo dõi»/«Bỏ theo dõi» button, which renders only on the redacted outsider view; counting CREATOR/ASSIGNEE rows would make an unfollow action delete a structural thread membership. `redacted` is set inside `toRedactedDetail` itself (not by the caller) so the flag can never desync from the mapping path that produced the response. `isFollowing` is null outside resident detail GETs — mutation responses and staff views have no follow semantics; FE only reads it when `redacted===true`.
**Also:** FE `canRate` gained `!redacted` guard — pre-existing cosmetic hole (outsider saw a rate form the BE would 403). Resident tickets page title 'Phản ánh của tôi' → 'Phản ánh' because the «Của tôi» tab now carries that meaning. 'mine' tab omits the visibility param entirely (relies on the P5 null-default) — sends no new query string, zero behavior change for pre-P7 flows.

## N3 close-out — CTO-ratified during browser smoke (2026-06-12)

**Ticket visibility is household-shared BY DESIGN:** all active residents of an apartment see and can act on the apartment's tickets (public or private) — pre-existing behavior since the original scoping (design §A.6: RESIDENT scope = own active apartment, never creator-only). Surfaced during the N3 smoke rounds; CTO confirmed intended, no change. The P5 redaction boundary is the HOUSEHOLD, not the creator.

## Hardening sprint rulings — CTO-ratified (2026-06-12, H1)

Authoritative record for the sprint (proposal: reports/hardening-design.md):
- **F-05 = RESOLVED** (E1): IDOR core closed by N3 P5 `enforcePhotoAccess` (96ae285); residuals shipped in H1 — presign expiry **1h → 10 min** (`FileStorageService.PRESIGN_EXPIRY_SECONDS = 600`, commit 0c72583) + normative file-surface access matrix in API-SPEC §13 (P-C).
- **Announcement images (N2): public-read-by-design** via `announcements/` key prefix (E3) — prefix routing in the presign check is H2.
- **Public-ticket photos: PERMANENTLY blocked** from public view (E4); any future exposure requires a per-photo creator-consent feature, never a presign widening.
- **TECHNICIAN NEW-status presign rule KEPT** (E5) — matches list/detail scoping; restricting it would desync three rules.
- **F-04 ≡ SEC-20 unified** — one item; the MEDIUM severity (F-04) governs.
- **F-04 remediation = Option 1, httpOnly refresh cookie** (E2, recorded now, implemented H3/H4): profile-driven `Secure` flag (prod-only — dev/demo runs http on ports 80/81 and would otherwise lock out all logins); `SameSite=Strict`; `Path=/api/auth`; CORS exact origins + allowCredentials; BE keeps the body-param fallback during H3 so H3 can ship before H4. H5 = CTO auth smoke (login / refresh-after-expiry / logout, BOTH apps) before the fallback is ever removed.

## H3 — dual-mode window OPEN (2026-06-12)

Refresh token now travels BOTH channels: response body (legacy, pre-H4 FE) AND httpOnly cookie. The legacy body channel (login-response `refreshToken` field + body-param refresh without header) is removed ONLY at sprint close-out AFTER the H5 browser smoke passes on both apps — never in H4 itself.

## H4 — FE cookie-based session; dev-topology cookie collision (2026-06-12)

**What:** Both apps dropped every `localStorage['gemek_refresh']` read/write. Refresh is cookie-implicit: `POST /auth/refresh` with empty body + `X-Requested-With: XMLHttpRequest` header, `withCredentials: true` on both axios instances and on the raw-axios refresh calls (bootstrap + 401 interceptor). Login ignores the body `refreshToken` (still sent by BE during the dual-mode window). One-time legacy cleanup `localStorage.removeItem('gemek_refresh')` runs at bootstrap — pre-H4 tokens must not linger (that lingering IS F-04). Reload bootstrap no longer short-circuits on missing localStorage token — it always attempts the cookie refresh (httpOnly cookies are invisible to JS, there is nothing to gate on); 401/no-cookie → `unauthenticated` → login screen, no loop (interceptor skip-list covers `/auth/refresh`, bootstrap uses raw axios anyway). Cost: unauthenticated visitors now pay one failing refresh POST per page load — accepted, unavoidable with httpOnly.

**Dev-environment limitation (testing instruction):** cookies are HOST-scoped, not port-scoped. On the dev nginx :80/:81 topology, admin and resident apps share `localhost` — logging into BOTH apps in ONE browser makes each login overwrite the other's refresh cookie (same name, same host, same path `/api/auth`). Dev-only limitation; prod separates domains. **H5 smoke testing must use two browser profiles (or one app per browser).**

## H5 point-6 — FE role-gate per portal (2026-06-15)

**Observed (smoke point 6):** in ONE browser, logging into admin (:80) then resident (:81) and returning to the admin tab showed the admin tab now running as the RESIDENT — a silent IDENTITY ADOPTION, not a kick-to-login. **Correction to the H4 cookie-collision note:** the prior note framed the shared host-scoped cookie purely as a session-overwrite/lockout nuisance; the real failure was worse — bootstrap restored whichever session the cookie currently named, and neither portal checked that the restored role belonged to it. So an admin-portal tab silently became a resident (and vice-versa).

**Fix (FE only, BE untouched):** client-side role-gate in both authStores at two points — (1) bootstrap, after cookie-refresh + `/auth/me`; (2) post-login, on the login-response `user`. Allowed sets from ground truth (FE `RequireRole` literals + BE enum `UserRole`): **admin = `['ADMIN','BOARD_MEMBER']`** (admin FE has zero TECHNICIAN references → technicians do not use this portal), **resident = `['RESIDENT']`**.

**Why local-only reset, never `/auth/logout` on mismatch:** logout revokes THAT user's refresh tokens server-side; if a legit user is simultaneously signed into their correct portal in another tab/profile, a logout triggered by the wrong-portal tab would kill their good session. Mismatch handling therefore drops in-memory state only (`accessToken`/`user` → null, `authStatus='unauthenticated'`). Post-login mismatch additionally throws an `Error` carrying error code `WRONG_PORTAL`, mapped by the existing `getVnErrorMessage` to «Tài khoản không có quyền truy cập cổng này.» (new key added to the shared `@gemek/ui` error map — no hardcoded string in components).

**Defense-in-depth, valid for prod too:** the role-gate is correct regardless of topology — a portal should never run a session whose role it does not serve. It is NOT merely a dev-topology workaround.

**Root fix still pending CTO topology decision:** cross-portal simultaneous login in one browser on the SAME host remains impossible — the two portals share one refresh-cookie name/host/path, so the second login still overwrites the first's cookie. The role-gate makes that collision FAIL SAFE (wrong-portal tab → login screen) instead of failing silently (identity adoption). If production ever serves both portals from one host, the durable fix is **per-portal cookie-name separation** (e.g. distinct cookie names or distinct sub-paths/domains); deferred pending the CTO's production-topology decision.

**Commits:** resident a2521e4, admin fe22555 (this docs commit separate).

## Hardening sprint CLOSE-OUT — cookie-only refresh + topology ruling (2026-06-16)

**Supersedes the "dev-only cookie limitation" framing in the H4 and H5 entries above.** Those entries treated the single-browser same-host cookie overwrite as the headline issue; the accurate, CTO-ratified ruling is:

- **Simultaneous two-portal use is DESIRED behavior, not a problem to design around.** In production the two portals are served from **separate subdomains** — `admin.<domain>` and `resident.<domain>` are distinct hosts with **independent cookie jars**, so an admin session and a resident session coexist in one browser with zero interference. The single-browser, same-host cookie overwrite observed in dev is purely a **DEV-ONLY artifact** of running both apps on `localhost:80/:81` (one host).
- **NEVER set a shared cookie `Domain` (e.g. `.<domain>`).** Cookies stay per-subdomain (host-only). A shared parent-domain cookie would re-introduce the cross-portal overwrite in production — exactly what the subdomain split avoids.
- **Production requires `app.auth.cookie-secure=true` (https).** The `Secure`-over-http lockout trap (dev/demo http) is why the flag is profile-driven and defaults false; prod MUST set it true.
- **The FE role-gate (H5 point-6 fix) is defense-in-depth that stays valid in production**, independent of cookie topology: a portal must never run a session whose role it does not serve. It blocks wrong-portal login regardless of how cookies are scoped.
- **Refresh is cookie-only as of this commit.** The H3 dual-mode body channel is removed: `/auth/login` and `/auth/refresh` no longer return `refreshToken` in the JSON body (`@JsonIgnore` on `LoginResponse.refreshToken`, kept only so the controller can build the cookie), and `/auth/refresh` accepts the httpOnly cookie as the sole token source (no cookie → 401; cookie without `X-Requested-With` → 403). Test-enforced in `AuthControllerTest` / `AuthCookieTest`.

**Audit reconciliation:** **F-04 ≡ SEC-20** are the SAME finding (double-logged MEDIUM/INFO); unified as one item, MEDIUM governs, status **FIXED** (httpOnly cookie, H3/H4/close-out). **F-05** RESOLVED in H1/H2. Both `SECURITY_AUDIT_PROGRESS.md` and `reports/security-remediation.html` updated. **Hardening sprint H1–H5 COMPLETE.**

## Backlog (c) — staff user-mgmt: CTO rulings (2026-06-17)

Rulings on the two open decisions from `reports/c-staff-usermgmt-investigation.md` (the investigation stays the evidence; this entry records only the decisions). Item (c) moves to IN PROGRESS.

1. **D1 = single-role model UNCHANGED.** A human who is both a resident and staff uses **two separate accounts** (two phones — phone is the unique login identifier, `uq_users_phone`). True multi-role (many-to-many) is **DEFERRED CONDITIONALLY**: revisit ONLY if a concrete "one person, two roles, in one session" requirement is confirmed. **Why:** multi-role is an authorization-core rewrite — single-authority → authority collection at `JwtAuthenticationFilter.java:134`, cascading to all 88 `@PreAuthorize` sites + every resident-scoping/IDOR guard — for a need not yet demonstrated. Cost of staying single-role is purely operational (two logins), no security risk.

2. **D2 = (A) extend the EXISTING admin portal.** Add `TECHNICIAN` to the admin allowed-set. **No** separate technician portal, **no** new subdomain / nginx server block / build pipeline. The topology close-out ruling (separate subdomains per portal, `cookie-secure=true` in prod) is unchanged.

3. **Technician UI scope = TICKETS ONLY** — queue/list + detail + status update. The other ~20 technician-authorized BE endpoints (parking / contractors / reads, investigation §2) are **NOT** surfaced in the technician UI for now.

4. **UsersPage = admin staff management over the EXISTING `/api/users` endpoints** (no new BE endpoints for basic CRUD — list/create/get/update/deactivate/reset-password already exist, ADMIN-only). Manages **ADMIN + TECHNICIAN + BOARD_MEMBER**. Access stays **ADMIN-only** — BOARD_MEMBER does NOT get staff-list read.

5. **Guardrail = UI confirmation for ADMIN role.** Creating a user with `role=ADMIN`, or promoting an existing user to `ADMIN`, requires an explicit confirmation step in the UI. (A backend DTO-level restriction on who may set `role=ADMIN` is a SEPARATE open question — this ruling covers the UI confirmation only.)

6. **Audit_logs persistence is SPLIT OUT as the final phase (P4) and MAY be done in its own session.** Removing the `AuditLogAspect` DEBUG-stub + adding the `audit_logs` table + writing real DB rows is independent — **item (c) does NOT depend on it**.

7. **Implementation order is FIXED for safety:** P1 build UsersPage → P2 audit & tighten `RequireRole` on ALL admin pages so a technician cannot reach any non-ticket page → **only THEN** P3 widen the admin allowed-set to admit `TECHNICIAN`. **Widening the gate before per-page `RequireRole` is in place is FORBIDDEN** — it would expose admin-only data to technicians (investigation §3: `tickets` + `dashboard` routes currently have NO `RequireRole`).

8. **All H5 role-gate invariants MUST be preserved by any (c) change:** role validated at BOTH gate locations (bootstrap + post-login) in each app; mismatch → **LOCAL state reset only, NEVER `/auth/logout`**; `WRONG_PORTAL` surfaced via `getVnErrorMessage`. See the H5 point-6 entry above.

**Phase plan:** P0 docs (this entry) → P1 UsersPage → P2 RequireRole audit → P3 admit TECHNICIAN (tickets-only) → P4 audit_logs (split, may be own session) → P5 docs (API-SPEC + user-guide). Recorded in `reports/module10-extended-backlog.md` item (c).

### Backlog (c) — P2.6: `overdue` filter on GET /api/tickets (2026-06-17)

Decision after `reports/c-reports-investigation.md` established a BE change is required for technician-reachable SLA-breached **regardless of Cách 1/2**. Chose the minimal one: a filter, not a permission change.

- **`overdue` Boolean filter added to GET /api/tickets** (the LIST endpoint that ALREADY admits TECHNICIAN and is ALREADY role-scoped). `@PreAuthorize` and `buildScopeSpec` UNCHANGED — this is a filter addition, exposes no new data. Param name `overdue` per CTO task (supersedes the never-implemented `slaBreached` placeholder in API-SPEC).
- **Predicate mirrors the canonical SLA-breach definition** — not invented: `sla_deadline IS NOT NULL AND sla_deadline < now AND status NOT IN (DONE, CANCELLED)`, identical to `TicketRepository` report aggregates (`:43/91/130/159`) and `findSlaOverdueCandidates` (`:184-186`). Count matches Reports `slaBreached` / dashboard `overdueRequests` for the same dataset by construction.
- **`overdue=false` semantics = logical complement** ("not overdue"), via `cb.not(breached)`. The leading `cb.isNotNull` makes a NULL deadline a definite non-match for `true`, so `cb.not` cleanly classifies NULL-deadline/future/closed as not-overdue. `null`/absent = no SLA filtering (existing behavior unchanged). Alternative (true-only active, false=no-filter) rejected — complement is well-defined and testable.
- **Null-safety:** Criteria API (`cb.isNotNull` + `cb.lessThan`), never a JPQL nullable-param comparison (Hibernate 6 / PostgreSQL learning). NULL deadline never matches `overdue=true`.
- **Verified:** suite 314/314; dev-DB canonical count 459 overdue-open / 603 total; integration test proves the real HTTP path; live :80 literal-459 deferred to the gated docker redeploy. Evidence: `reports/c-p2.6-overdue-filter.md`.
- **FE consumption = P2.7** (not started): SLA-breached card via `useTicketCount({overdue:true})` + `?overdue=true` drill-down. Cách 2 confirmed — ruling 3 (technician UI = TICKETS ONLY) preserved.

### Backlog (c) — P2.8: server-derived `mine` filter on GET /api/tickets (2026-06-17)

Decision after `reports/c-assignee-filter-investigation.md` established the assignee filter does NOT exist; a gated BE change is required before the FE «Phân công cho tôi» card. Mirrors the P2.6 `overdue` pattern.

- **`mine` Boolean filter added to GET /api/tickets** (same LIST endpoint, admits all roles, already role-scoped). `@PreAuthorize` and `buildScopeSpec` UNCHANGED — filter on top of scope, exposes no new data.
- **Server-derived, NOT a client-supplied `assigneeId`.** Target = `principalId` (the same caller id `buildScopeSpec` uses). The FE passes no user id → nothing to forge. An `assigneeId=<id>` param was rejected: it would let any admin enumerate another staff member's workload (IDOR) and is YAGNI for this card. `principalId` is now also threaded into `buildFilterSpec`.
- **`mine=false` semantics = NO-OP** (no assignee filtering, identical to absent). Only `mine=true` is active. **This INTENTIONALLY DIVERGES from P2.6's `overdue=false`=complement.** Rationale: "not assigned to me" is not a product need, would muddy the «Phân công cho tôi ✕» chip, and YAGNI (per investigation §4). Guarded with `Boolean.TRUE.equals(mine)` so both `false` and absent skip the predicate — existing behavior byte-for-byte unchanged.
- **Null-safety:** Criteria API `cb.and(cb.isNotNull(assignedToUser.id), cb.equal(assignedToUser.id, principalId))` — never a JPQL nullable comparison. An UNASSIGNED ticket (assignee NULL) can never match `mine=true` (the `assignedToUser.id` path also forces an INNER join that independently drops nulls; the explicit `isNotNull` documents intent).
- **Scope composition:** ANDed on top of `buildScopeSpec`. TECHNICIAN `(assigned-to-me OR NEW) AND mine` collapses to assigned-to-me (strict subset, no bypass). Composable with `overdue` (AND).
- **Verified:** suite 319/319; dev-DB canonical admin-assigned 23 / 705 total; integration tests prove the real HTTP path; live :80 literal-23 deferred to the gated docker redeploy (running container is old image). Evidence: `reports/c-p2.8-mine-filter.md`.
- **FE card phase = NEXT** (not started): «Phân công cho tôi» StatCard via `useTicketCount({mine:true})` + `?mine=true` drill-down + «Phân công cho tôi ✕» clearable chip.

### Cross-session identity confusion — investigated, NOT a defect (2026-06-18)

Full evidence: `reports/c-identity-confusion-diagnosis.md`. Verdict: dev/test artifact of a shared cookie jar; backend proven clean. **No code change.**

- **Symptom:** two incognito windows on the admin site — W1 logged in as TECHNICIAN, W2 logged in later as BOARD_MEMBER; returning to W1 it showed the BOARD_MEMBER view (after a bootstrap).
- **Root cause:** the host-scoped httpOnly `refreshToken` cookie (single fixed name, `AuthController.java:57`, Path `/api/auth`). Two incognito **windows of the same incognito session share ONE cookie jar** — the "separate partitions" assumption was wrong. W2's login overwrote the single cookie (`AuthController.java:107`); W1 re-adopted it on its next bootstrap (`/auth/refresh` → `/auth/me`, `authStore.ts:64-78`). This is identity **REPLACEMENT by a legitimately-authenticated second user** in a shared jar — NOT an unauthorized leak; W1 gains nothing W2 did not log in as with their own credentials.
- **Backend proven clean (no cross-request/cross-session bleed exists server-side):** `SecurityContextHolder` = default `MODE_THREADLOCAL` (no `setStrategyName`, no inheritable/global strategy); no singleton/static field holds the principal (`JwtAuthenticationFilter` deps all `final`; `getMe(principal)` is a method arg; every `private User user` is a JPA entity); principal resolved per-request from the request's own token, user loaded from the DB each request (SEC-06 intact, `JwtAuthenticationFilter.java:118-134`); Redis keys are user+jti scoped (`AuthServiceImpl.java:140/179/227`); no `HttpSession` anywhere (stateless JWT).
- **Production: self-resolves.** Separate subdomains per portal → separate cookie jars per host (the topology close-out ruling). Consistent with the prior "cookie-overwrite is dev-only" ruling — the second login can only clobber the first within ONE shared jar on ONE host.
- **Severity: LOW / informational.** Not privilege escalation, not cross-user data theft. Optional, non-security FE hardening logged as backlog item (f).

### Standing rule — API-SPEC sync on every API change (2026-06-18)

`docs/API-SPEC.md` MUST be updated in the SAME phase + same `docs(context)` commit group whenever an
endpoint's path/method/params/request/response shape/`@PreAuthorize` changes (added/removed/changed).
A phase shipping an API change without the matching API-SPEC update is INCOMPLETE. Recorded in
`CLAUDE.md` → "API-SPEC Sync (mandatory)". First reconciliation under this rule:
`reports/c-p5-apispec-reconciliation.md` (spec bumped v2.1→v2.2: +10 endpoints, 10 mismatches fixed,
4 stale entries flagged for ruling).

### AUD.1 — Adopt Spring Data JPA auditing foundation (2026-06-18)

Report: `reports/aud1-jpa-auditing.md`. Plan: `reports/audit-columns-investigation.md`.

- **What:** `V17` adds nullable `uuid` `created_by`/`updated_by` (+ FK `users(id) ON DELETE SET NULL`) to 17 tables — both columns on 12 mutable, `created_by` only on 5 append-only. `@EnableJpaAuditing(auditorAwareRef="auditorAware")` + `AuditorAware<UUID>` (`SecurityAuditorAware`) + base `@MappedSuperclass` (`AuditableEntity`/`CreatableEntity`); 17 entities wired.
- **Why UUID actor (not `@ManyToOne User`):** `UserPrincipal` already holds the id → no `userRepository` load per save; FK keeps DB-level integrity. Matches existing creator-column precedent.
- **Why empty AuditorAware on unauthenticated:** system/seed/scheduler/Flyway/login writes have no principal → `Optional.empty()` → column stays NULL, no NPE. Verified Spring Data `touchAuditor` early-returns on empty, so the login `last_login_at` save does not null an existing `updated_by`.
- **Why keep manual timestamps:** avoid churn — base class adds ONLY actor fields; `@PrePersist`/`@PreUpdate` `created_at`/`updated_at` unchanged. Both entity-listener and entity-method callbacks fire (JPA spec).
- **Deferred:** Contract/Announcement convergence (AUD.2); `AuditLogAspect`/`@Auditable` removal (AUD.3). No `created_by_user_id` rename (CTO ruling).
- **Alternatives rejected:** `AuditorAware<User>` (per-save DB hit, can't share a UUID base cleanly); plain UUID without FK (loses integrity, diverges from precedent).

## AUD.2 — Contract/Announcement converge onto Spring Data auditing (Option B1) — 2026-06-22

- **What:** `contracts`/`announcements` `createdBy` migrated from `@ManyToOne User` (set manually) to `@CreatedBy UUID` via `AuditableEntity`; added `@LastModifiedBy UUID updatedBy` (V18 `updated_by` col + FK `users(id) ON DELETE SET NULL`). Manual `setCreatedBy` removed at both entity-write sites → auditing is the sole writer.
- **Why `@AttributeOverride` over a new column:** CTO ruled `created_by_user_id` is NOT renamed. The inherited `createdBy` field (col `created_by`) is remapped via `@AttributeOverride(column=@Column(name="created_by_user_id", updatable=false))` so the field type changes (`User`→`UUID`) while the DB column name is preserved. `updatable=false` keeps the creator immutable.
- **Why batch name resolution (not per-row find):** response DTOs still expose `createdBy.fullName`; with `createdBy` now a UUID the name must be resolved app-side. Done in ONE `userRepository.findAllById(distinct ids)` per page (MapStruct `@Context` map for Contract; `resolveCreatorNames` for Announcement) → no N+1. Guarded by two unit tests (findAllById once / findById never).
- **Report's "3rd manual site" clarified:** `AnnouncementServiceImpl:521` `.createdBy(creatorRef)` is the response-DTO builder, not an entity write. Only 2 real entity-write sites existed; both removed. Post-change grep `setCreatedBy|.createdBy(` over `src/main` = 0 entity writes.
- **Scheduler ripple:** `ContractExpiryScheduler` / `MaintenanceScheduleRunner` notified `contract.getCreatedBy().getId()`; now `contract.getCreatedBy()` (already the UUID).
- **API-SPEC:** unchanged — `createdBy:{id,fullName}` shape preserved.
- **Test fixture note:** entities whose `@CreatedBy` must be a specific actor in a test now authenticate before persist (e.g. `ContractExpiryOnceOnlyTest`) — a manual `setCreatedBy` is overwritten to null by the auditing listener on persist when no principal is present.

## AUD.3 — AuditLogAspect + @Auditable removed; auditing consolidated on created_by/updated_by (2026-06-22)

Report: `reports/aud3-remove-aspect.md`. Plan: `reports/audit-columns-investigation.md` §5 + §C. Completes the AUD chain (auditing rework).

- **What:** Deleted `AuditLogAspect` (the `@Aspect @Component` write path) and the `@Auditable` annotation type (its only 4 usages — `UserServiceImpl.createUser/updateUser/deactivateUser/resetPassword` — removed first, then the now-orphaned annotation file). Removed the dead `Auditable` import. Create/update attribution is now captured **system-wide** via Spring Data `created_by`/`updated_by` (`@CreatedBy`/`@LastModifiedBy` + `SecurityAuditorAware`, landed in AUD.1/AUD.2) — the aspect is redundant.
- **INTENTIONALLY no longer captured (knowing CTO trade-off, NOT a regression):** (1) **reset-password actor attribution** — `RESET_PASSWORD` wrote no entity column, only an `audit_logs` row; with the aspect gone, who reset a password is no longer recorded; (2) **full before/after change-history** — the aspect's per-action `audit_logs` rows (action label + entity id) are no longer written. `created_by`/`updated_by` capture only *latest* create/update actor, not a change log. CTO ruled this acceptable when adopting Spring Data auditing (recorded in `reports/audit-columns-investigation.md` §"CTO rulings", line 14).
- **KEPT, write-idle (NOT dropped — destructive/irreversible):** `audit_logs` TABLE + `V10` migration untouched (historical rows preserved); `AuditLog` ENTITY + `AuditLogRepository` retained (table still exists; orphaning the mapping would be odd). They are now read-only/write-idle — javadoc on both updated to say so. A future deliberate `Vxx` migration may drop the table if the history is ever no longer wanted; **AUD.3 does not.**
- **Blast radius confirmed before delete:** grep proved `@Auditable`/aspect referenced by exactly the 5 files in §5; `AuditLogRepository` was injected ONLY by the aspect; the aspect's `SecurityContext` read was self-contained (nothing else depended on the aspect *running* — `SecurityAuditorAware` is the auditing reader now). No test asserted on `audit_logs` rows (grep `AuditLog|audit_logs` over `src/test` = 0), so no audit-row assertion needed updating.
- **Tests:** added 3 unit tests to `UserServiceImplTest` proving the user actions still WORK without the aspect — `deactivateUser` (valid target deactivates), `resetPassword` (re-encodes + saves new hash), `resetPassword` NOT_FOUND. create/update already covered. Baseline 336 → **339/339 green**.
- **API-SPEC:** no endpoint change (no path/method/param/response/`@PreAuthorize` touched) → unchanged.

## Apartment occupancy derived (AVAILABLE/OCCUPIED), MAINTENANCE stored-priority, 3 surfaces converged — 2026-06-22

Reports: `reports/apartment-occupancy-diagnosis.md` (root cause) + `reports/apartment-occupancy-fix.md`.

- **The rule (single source):** `OccupancyResolver.effective(stored, hasActiveResident)` — `MAINTENANCE` if stored==MAINTENANCE (priority); else `OCCUPIED` if ≥1 active resident (`move_out_date IS NULL`); else `AVAILABLE`. ONE class, used by apartment list, apartment detail, dashboard KPI, and resident report. The active-resident *fact* is the `move_out_date IS NULL` predicate, expressed only in `ResidentRepository` occupancy queries.
- **CTO ruling honored:** AVAILABLE/OCCUPIED are DERIVED (never read from the stored field); MAINTENANCE remains a stored, manually-set state that takes priority over occupancy.
- **OCCUPIED is derived-only, NEVER stored** (new convention). Migration `V19` normalizes the 10 manually-set stored OCCUPIED rows → AVAILABLE; the stored column now only ever holds AVAILABLE or MAINTENANCE. The `OCCUPIED` enum value is kept (still a valid computed/response value). No 1612-row data-fix — derivation fixes those for free.
- **Convergence (the secondary bug):** dashboard and resident-report now call the SAME private `ReportServiceImpl.computeOccupancy(blockId)` → identical numbers by construction. Dev DB: was dashboard 10 / report 1622 (disagree); now both 1622 / ≈71.5% (agree). Removed `ApartmentRepository.countByStatus` (read the never-synced field).
- **N+1:** list batch-fetches the page's active residents in ONE `findActiveByApartmentIdIn(pageIds)` query (occupancy + primary-contact both derived from it); dashboard/report use one `[id,status]` projection + one occupied-id set. Guarded by a unit test (batch once / per-row never). This also eliminated the pre-existing per-row primary-contact query.
- **Response shape unchanged:** apartment `status` field keeps its name/enum type; only the VALUE is now computed → FE needs no change.
- **~~Deferred (recorded)~~ RESOLVED (2026-06-22, see next entry):** the list `?status=` query filter now derives effective status in SQL, matching the display. The earlier "deferred filter" caveat is removed from API-SPEC.

### 2026-06-22 | Apartment `?status=` filter derives effective status (resolves filter/display mismatch)

**Context:** The occupancy DISPLAY fix made list/detail/dashboard derive AVAILABLE/OCCUPIED from active residents, but the `?status=` filter still matched the STORED column. Post-V19 the stored column only holds AVAILABLE/MAINTENANCE, so `?status=OCCUPIED` returned empty and `?status=AVAILABLE` returned occupied units too — the filter actively contradicted the display (CTO smoke). Not deferrable: the display fix broke the filter.

**Decision:** Filter by EFFECTIVE status in the paginated SQL query (`ApartmentRepository.findAllByEffectiveStatus`, called via the `findAllWithFilters` default method):
- `OCCUPIED` → `a.status <> MAINTENANCE AND EXISTS(active resident, move_out_date IS NULL)`
- `AVAILABLE` → `a.status <> MAINTENANCE AND NOT EXISTS(active resident)`
- `MAINTENANCE` → `a.status = MAINTENANCE` (priority, residents irrelevant)
- no filter → all.

**Single-source / drift prevention:** the SQL predicate is the textual mirror of `OccupancyResolver` (MAINTENANCE-priority + the `move_out_date IS NULL` active-resident fact). Both the resolver javadoc and the repository javadoc cross-reference each other and state the predicate MUST stay in lock-step. The `ApartmentStatusFilterIntegrationTest` filter↔display agreement test asserts each `?status=X` returns exactly the apartments whose displayed status is X — it fails if the SQL and the resolver ever drift.

**Count-query consistency:** kept as a single `@Query`; Spring derives the count query from the SAME JPQL, so the count and row queries apply the IDENTICAL effective-status WHERE clause — `total` can never disagree with the rows. A test asserts `total == data.length` per status.

**Enum-typing gotcha:** a fully-qualified enum LITERAL in JPQL (`...ApartmentStatus.MAINTENANCE`) made Postgres reject the query (`type "apartmentstatus" does not exist` — bad cast). Fix: pass the requested status as its NAME (string branch selector, no anchoring needed) and bind `MAINTENANCE` as an enum PARAMETER, which Hibernate anchors against `a.status` and renders with the correct `apartment_status` type. Same class of fix as the recipient-query default-method pattern.

**Performance:** one in-SQL query; the EXISTS subquery on `residents(move_out_date IS NULL)` is not an N+1.

**Verified:** suite 358/358; dev-DB effective counts via the identical predicate: OCCUPIED 1622, AVAILABLE 647, MAINTENANCE 0, total 2269 (1622+647+0=2269, matches the derived display breakdown). Evidence: `reports/apartment-filter-fix.md`.

### 2026-06-22 | Occupancy fully derived → apartment status is NOT client-settable; MAINTENANCE hidden in UI

**Context:** With occupancy DISPLAY derived (`OccupancyResolver`) and the `?status=` FILTER derived in SQL,
the stored `apartments.status` column only ever holds AVAILABLE/MAINTENANCE post-V19. The update endpoint
still accepted a client-supplied `status` (`setStatus(request.status())`), letting an admin persist an
OCCUPIED/MAINTENANCE value that contradicts the derived display — a desync hole. The admin UI also still
offered MAINTENANCE (filter dropdown + a free status `<select>` in the edit form) despite there being no
maintenance set-flow.

**Decision:** Status is **not client-settable** via the apartment update endpoint.
- Removed `status` from `UpdateApartmentRequest` (DTO) and `setStatus(request.status())` from
  `ApartmentServiceImpl.updateApartment`. Verified the only `status` writers were create (hardcoded
  AVAILABLE — kept) and update (removed); after this, nothing client-driven sets status. Apartments keep
  their stored status; the response status is the derived effective status.
- Frontend hides MAINTENANCE: removed from the `?status=` filter dropdown; the edit-form status control is
  now read-only (derived display); `status` no longer sent in the update payload. Badge keeps the
  MAINTENANCE colour for graceful legacy rendering.

**Explicitly retained for re-enable:** `OccupancyResolver`, the `ApartmentStatus` enum (incl. MAINTENANCE),
and the BE `?status=MAINTENANCE` filter are UNTOUCHED. MAINTENANCE is hidden in the UI only.

**Alternatives considered:** (1) keep status settable but validate against derivation — rejected: still lets
a stored value drift and duplicates the rule. (2) Make the edit field read-only but keep it on the DTO —
rejected: a stale field a client could still POST; cleaner to remove it entirely (Spring ignores unknown
JSON properties, so an old client sending `status` is silently ignored, not 400).

**Deferred backlog:** *maintenance set flow* — when a real maintenance workflow is needed, add a dedicated
intentional set path (not a free status field on the generic update) and unhide the UI filter option.

**Verified:** backend suite 359/359 (358 + new status-not-settable guard); admin `tsc && vite build` green.
TDD: new guard `ApartmentServiceImplTest.updateApartment_doesNotChangeStoredStatus`; adjusted
`ApartmentStatusFilterIntegrationTest.setMaintenance` to stamp MAINTENANCE via repo. Evidence:
`reports/apartment-status-lockdown.md`.

### 2026-06-22 | Residency lifecycle — domain model (CTO-approved)

**Context:** A recurring confusion conflates three things as one "resident". Hitting the
*cannot-register-with-a-moved-out-resident's-phone* problem forced the model to be made explicit. This
entry records the CTO-approved conceptual model; it builds on (does not contradict) the move-out
conditional-deactivation decision (`2026-06-22 | (d) follow-up — Move-out conditional user deactivation`)
and the occupancy-derivation decision (`2026-06-22 | Occupancy fully derived → apartment status is NOT
client-settable`).

**Decision — three DISTINCT entities:**
1. **IDENTITY = a human.** Identified by **phone number**, which is **PERMANENT and NON-REUSABLE**: a
   different person can never reuse an old phone — a new human always registers a new phone. (Corollary: a
   phone always maps to the same single human, forever.)
2. **ACCOUNT = login.** One account per identity (`users` row). Can be active/inactive.
3. **RESIDENCY = one stay of one person in one apartment**, bounded by move-in / move-out dates
   (`residents` row).

**`residents` is the RESIDENCY HISTORY table.** One user (one permanent-phone identity) may hold
**MULTIPLE** resident records:
- **Concurrently** — living in 2+ apartments at once. **⚠️ CORRECTION (2026-06-22, see investigation
  §A):** the original wording below was FACTUALLY WRONG and is retained struck-through for the record.
  > ~~(confirmed allowed). Only one *active* row per (user, apartment) is enforced by the
  > `uq_residents_active_user` partial unique index family; multi-apartment concurrency is permitted.~~

  **Verified truth:** the CURRENT live index `uq_residents_active_user` is partial-unique on **`user_id`
  ALONE** (`V4__create_residents_vehicles.sql:22`, confirmed against live dev DB), NOT on
  `(user_id, apartment_id)`. It therefore **FORBIDS concurrent multi-residency TODAY** — at most one active
  residency per user across all apartments. Concurrent multi-residency is a **CTO-approved TARGET** (see
  the ruling sub-entry below), not a current capability. Enabling it requires, in order: **(a)** a
  call-site sweep of `findActiveByUserId` consumers, then **(b)** a later migration relaxing the index to
  `(user_id, apartment_id) WHERE move_out_date IS NULL`. Evidence: `reports/residency-lifecycle-investigation.md §A`.
- **Over time** — lived in A (moved out), later in B. Move-out is **soft** (set `moveOutDate`); the row is
  **retained as history**, never deleted.

**MOVE-OUT (already implemented this way):** soft — set `moveOutDate`, retain the record. Deactivate the
linked account ONLY when the user has **no remaining active residency in ANY apartment**
(`residentRepository.existsActiveByUserId` checked AFTER `moveOutDate` is set). Conditional, NOT 1-to-1 with
a single residency — correct under multi-residency.

**MOVE-IN / RETURN (target flow — see backlog):** look up the user **by phone** first.
- If the user **already exists** (a returning person — *always the same human*, since phone is permanent):
  **REUSE** that user, add a **NEW** `residents` row, and **reactivate** the account if it was disabled. Do
  NOT attempt a fresh registration.
- Only **create a new user** when the phone is **genuinely new**.
- There is **NO** "new person reusing an old phone" case — explicitly ruled out by the permanent-phone rule.

**OCCUPANCY is fully DERIVED** from active residents (not stored) — see the occupancy-derivation decision.
Derivation counts active residency presence per apartment, so it supports multi-residency automatically (a
user active in A and B makes both apartments OCCUPIED with no extra modelling).

**OPEN considerations for the design session — UPDATED 2026-06-22 with investigation §E/§F findings:**
- **✅ CLOSED — `primaryContact` scope (verified-correct, no change needed).** Investigation §E verified
  clearing IS already **per-residency / per-row**: `ResidentServiceImpl.moveOut` clears the flag only on the
  single moved-out row (the entity loaded by `findById(id)`), and `clearPrimaryContactInApartment` is
  apartment-scoped — neither is user-wide. Moving out of A does NOT touch the same user's primary-contact
  flag on B. No user-wide clearing bug; correct under multi-residency as-is.
- **✅ VERIFIED — `existsActiveByUserId` (§E).** Counts active residency across ANY apartment for the user
  (not apartment-scoped), so move-out deactivation correctly fires only when no OTHER active residency
  remains anywhere. Correct under multi-residency.
- **Other one-user-one-apartment assumptions (§F risk levels).** Audited for what would break IF the index
  were relaxed: **tickets** (7 guards) and **`/residents/me`** are **HIGH** (the real work for P1; see ruling
  sub-entry); amenity / announcement feed / vehicle owns-check are **MED**; **notifications** (recipient
  fan-out is `DISTINCT u.id`, multi-residency-safe), **parking** (apartment-scoped assignments), and
  **billing/fees** (no per-resident charging exists) came out **LOW / N-A** — left as-is. Cross-reference
  `reports/residency-lifecycle-investigation.md §F` for the full risk table.

---

### 2026-06-22 | Residency lifecycle — CTO ruling on concurrent multi-residency + phased plan

**See:** the model entry above (now corrected re: the index) and `reports/residency-lifecycle-investigation.md`.

**RULING (CTO, authoritative):** concurrent multi-residency — one user holding **≥2 active `residents` rows
in different apartments at the same time** — **IS a real business requirement** (residents genuinely living
in 2+ apartments at once) and **WILL be supported**. The current schema does not allow it yet (the live
`uq_residents_active_user` index is partial-unique on `user_id` alone, §A); it will be enabled by relaxing
that index AFTER a call-site sweep.

**Phased plan (gate-controlled, STRICT order):**
- **P0 — DOCS reconcile (this entry).** Correct the index factual error in the model entry + record this
  ruling and plan. No code, no migration, no index change.
- **P1 — call-site sweep** of `findActiveByUserId` consumers → convert to list-returning + define a
  per-surface **"which residency"** semantic (the auth/permission gate per surface), landed **WHILE the
  index still enforces single-active** (so build + runtime both stay green). CTO-gated.
- **P2 — index-relax migration** to `(user_id, apartment_id) WHERE move_out_date IS NULL` + allow a 2nd
  active row. Migration-gated. **ONLY after P1 is green.**
- **P3 — move-in / return flow:** reuse-by-phone (existing user → REUSE) + new `residents` row + reactivate
  disabled account + append `resident_history`. (See backlog entry below.)

**HARD ordering constraint (WHY P1 before P2, index NEVER relaxed first):** `findActiveByUserId` is an
`Optional`-returning `@Query` with **no `LIMIT`** (`ResidentRepository.java:91`). If the index were relaxed
first, any user with 2 active rows would make that query throw **`NonUniqueResultException` at runtime** —
the build stays green but `/residents/me` and all 7 ticket guards break in production. So the sweep MUST land
**before or with** the index relax; the index is **never** relaxed ahead of the sweep. Evidence: investigation
§A, §F.

**~11 singular consumers P1 must address (from §A/§F):**
- **`/residents/me`** — `ResidentServiceImpl.getMyResident` `findActiveByUserId(...).orElseThrow` — **HIGH**.
- **7 ticket guards** in `TicketServiceImpl` (`:219, 360, 607, 873, 906, 933, 970`) — ownership/visibility
  `findActiveByUserId(...) → .getApartment().getId()` equality — **HIGH** (most pervasive).
- **amenity booking** (`AmenityServiceImpl`), **announcement resident feed** (`AnnouncementServiceImpl`),
  **vehicle owns-check** (`VehicleServiceImpl.verifyResidentOwnsApartment`) — **MED**.

**DEFERRED to the P1 CTO ruling (open item, NOT decided here):** the exact **"which residency"** semantic per
surface (e.g. does `/residents/me` return all active residencies, a default/primary one, or require an
apartment context? does a ticket guard match if the user is active in ANY of their apartments?). P1 will
propose these per-surface and gate for CTO approval.

---

### 2026-06-22 | Residency lifecycle — P1 findActiveByUserId sweep (AS IMPLEMENTED)

**See:** the CTO ruling entry above; `reports/p1-findactivebyuserid-sweep.md`. **Status:** P1 DONE, awaiting
CTO smoke. Index NOT touched (single-active still enforced); single-residency behavior identical to before.

**What:** every consumer of `ResidentRepository.findActiveByUserId` (singular `Optional`, no `LIMIT` — would
throw `NonUniqueResultException` once the index is relaxed) was converted to a multi-residency-safe query. The
per-surface "which residency" semantics deferred at the ruling are now DECIDED + IMPLEMENTED:

1. **`/residents/me` → ALL.** `getMyResident` returns `List<ResidentResponse>` (new `findAllActiveByUserId`,
   JOIN FETCH user+apartment+block, ordered primaryContact desc, moveInDate desc, id desc). Empty = `200 []`
   (was `404`). **Contract change** — `docs/API-SPEC.md §Residents` updated; resident FE updated to the array
   shape (chosen over `{residencies:[]}` because the hook returns `r.data` raw and consumers index it).
2. **Ticket ownership/visibility guards → PER-CONTEXT** (5 single-ticket sites: createTicket, uploadPhotos,
   enforceReadAccess, enforcePhotoAccess, isHouseholdMember) via new
   `existsActiveByUserIdAndApartmentId(principalId, ticketApartmentId)`. Strictly more correct: act on a ticket
   IFF actively residing in THAT ticket's apartment.
3. **Ticket "mine" list scope + redaction → ALL** (`buildScopeSpec` mine-tab `:970` and listTickets redaction
   `:219`) via new `findActiveApartmentIdsByUserId` (one batch query; `apartment.id IN set` / `Set.contains`).
   **Reconciliation noted:** §A/ruling bucketed `:970` among the "6 guards", but `:970` is structurally a
   list-scope (no single ticket to compare against) → it fits the ALL semantic, not PER-CONTEXT. Applied as ALL.
4. **Announcement resident feed → ALL, DISTINCT-by-id.** One Specification union across all active apartments
   (`publishedForResidenciesSpec`): scope ALL ∪ BLOCK∈blocks ∪ FLOOR per exact (block,floor) pair, with
   `query.distinct(true)`. No per-apartment loop+concat (which would duplicate ALL/BLOCK announcements). FLOOR
   matched per-pair to prevent cross-apartment floor leakage.
5. **Vehicle owns-check → PER-CONTEXT** (`verifyResidentOwnsApartment` → `existsActiveByUserIdAndApartmentId`).
6. **Amenity booking + listBookings → SAFE TEMPORARY, [PLANNED].** Deterministic primary-or-latest residency
   (first of `findAllActiveByUserId`) via private `resolveActiveResidency`. Code marked
   `// [PLANNED] multi-residency: temporary primary-or-latest selection; real attribution rule pending CTO ruling`;
   API-SPEC carries a `[PLANNED — multi-residency attribution]` note. **The real "which apartment is a booking
   charged to" rule is NOT decided** — deferred to CTO. Single-residency behavior unchanged.

**`findActiveByUserId`:** retained (NOT deleted — separate cleanup) with a `@deprecated`-style Javadoc note
(unsafe under multi-residency). No production code calls it after the sweep; the
`AnnouncementRecipientConsistencyTest` oracle was migrated off it to `findAllActiveByUserId`.

**Verified:** backend suite **366/366** green; resident `tsc && vite build` green. `/code-review` (high effort,
2 reviewers): backend clean, no single-residency regressions; FE break + import-order + consistency-oracle
findings resolved; one pre-existing string-concat in an error message left as-is (codebase-wide idiom, out of
P1 scope). TDD RED→GREEN demonstrated (announcement no-duplicate proof against the naive concat impl).

**Next (P2, gated):** relax `uq_residents_active_user` to `(user_id, apartment_id) WHERE move_out_date IS NULL`
(migration gate) — ONLY after CTO smokes P1.

---

### 2026-06-23 | Residency lifecycle — P2 index relax (AS APPLIED)

**See:** the CTO ruling + P1 as-implemented entries above; `reports/p2-index-relax.md`. **Status:** P2 DONE,
awaiting CTO smoke. Concurrent multi-residency is now ENABLED at the DB level.

**What:** the partial unique index `uq_residents_active_user` was relaxed from **`(user_id)`** to
**`(user_id, apartment_id)`** (both `WHERE move_out_date IS NULL`, same index name). Migration
`V20__relax_uq_residents_active_user_per_apartment.sql` — index-only (`DROP INDEX` + `CREATE UNIQUE INDEX`),
**no data DML**, no table/column change. The **single-active-per-user constraint is RETIRED**; the enforced
invariant is now "at most one ACTIVE residency per (user, apartment) pair" — a user may hold ≥2 active
`residents` rows in different apartments simultaneously (the CTO-approved concurrent multi-residency target).

**Ordering honored (sweep-before-relax):** P1 made every singular `findActiveByUserId` consumer multi-safe
FIRST (confirmed in code: no production caller of the singular query remains). Only then was the index
relaxed, so no path throws `NonUniqueResultException` under 2 active rows. Pre-flight read-only check on dev
`gemek` confirmed **zero** duplicate active (user, apartment) pairs, so the new unique index built without
violation (a same-pair duplicate could not pre-exist — the old `(user_id)` index already forbade ALL
multi-active rows per user).

**Applied:** dev `gemek` via the normal pipeline (`docker compose up -d --build backend` → Spring Boot Flyway
migrate on boot; `flyway_schema_history` V20 success=`t`). **No `flyway clean`.** Before→after live index def:
`(user_id) WHERE move_out_date IS NULL` → `(user_id, apartment_id) WHERE move_out_date IS NULL`.

**Proven (integration, real path):** `MultiResidencyIntegrationTest` (gemek_test, `@Transactional`) inserts the
2nd active row through the real repository (hitting the live index, not bypassing it) → succeeds; same-pair
duplicate → `DataIntegrityViolationException` (Postgres 23505); then re-exercises the HIGH P1 surfaces under the
genuine 2-active state (/residents/me both, ticket per-context allow/deny + household both, announcement
no-duplicate, amenity no-throw). 5/5 green; full suite 371/371 (0 fail/err). `/code-review`: **APPROVED, no
Must-fix.**

**Decision — `DROP INDEX IF EXISTS` NOT applied (knowing trade-off).** The reviewer suggested guarding the
`DROP` with `IF EXISTS`. Deliberately declined: V20 had ALREADY been applied to dev `gemek` and is recorded in
`flyway_schema_history` with a checksum. Editing the migration file now would cause a Flyway checksum mismatch
and fail the next backend boot / CTO smoke. The flyway rule "never modify a migration that has run in any
environment" governs. The unguarded `DROP` is safe in the normal pipeline because V4 always creates the index
first. A future hardening, if ever wanted, must be a NEW migration, not an edit to V20.

**Still open (unchanged):** amenity booking attribution remains `[PLANNED]` (temporary primary-or-latest;
real "which apartment is a booking charged to" rule pending CTO). **Next: P3** — move-in/return reuse-by-phone
flow (existing user → REUSE + new `residents` row + reactivate disabled account + append `resident_history`).

---

### Backlog — Residency lifecycle: move-in / return flow + multi-residency  *(NOT STARTED — needs design session)*

**See:** `2026-06-22 | Residency lifecycle — domain model (CTO-approved)` (above).

**Problem already hit:** registration cannot create a user whose phone belongs to a **moved-out** resident
(phone uniqueness rejects it) — but per the domain model that phone IS the same returning human, so the
system must **reuse the existing user and add a new residency**, not register a new user. Today there is no
move-in/return flow that does this; the operator is blocked at "phone already exists".

**Scope (to design):**
- A **move-in / return** flow keyed on phone: existing user → reuse + new `residents` row + reactivate
  account if disabled; genuinely-new phone → create user. Replaces the "new registration" assumption for
  returning residents.
- **Multi-residency correctness** sweep: `primaryContact` per-residency scoping (move-out of A must not
  affect B), plus notifications / parking / billing assumptions (see OPEN considerations above).

**Status:** NOT STARTED — requires a dedicated residency-lifecycle design session before implementation.

---

### 2026-06-23 | Announcements rich content C1 — Markdown body + XSS-safe render (CTO-ruled, implemented)

**Ruling (CTO, authoritative):**
- **Format = Markdown** stored raw in the existing `content TEXT` column. **NO** schema change, **NO**
  `content_type` column.
- **XSS = defense-in-depth:** safe render on FE (primary) + lightweight write-check on BE (secondary).
- Published announcements remain **IMMUTABLE** (only drafts editable — unchanged). Feed scope query,
  publish/dispatch tx untouched. C1 does **not** touch media/MinIO/presign.

**Implementation:**
- Shared `MarkdownContent` renderer in `@gemek/ui` (`packages/ui`) — single source of truth, consumed by
  BOTH resident detail and admin preview so the safe config cannot drift. Config: `react-markdown` 9.0.1
  with **no `rehype-raw`** (raw HTML not rendered), **no `dangerouslySetInnerHTML`**, element allowlist
  (img excluded — images deferred to C2), scheme-filtered URLs (http/https/mailto only; `javascript:`/
  `data:` neutralised), links `rel="noopener noreferrer"` + external `target="_blank"`, `remark-breaks`
  for legacy single-newline behavior.
- BE write-check in `AnnouncementServiceImpl.validateContent` (create + update): max length **20000 chars**
  (`ANNOUNCEMENT_CONTENT_TOO_LONG`) + raw-HTML reject (`ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`), regex
  `</?[a-zA-Z][a-zA-Z0-9]*[\s/>]` (does not trip on Markdown `<url>`/`<email>` autolinks or a bare `<`).
  No heavy BE sanitizer dep added.

**Alternatives considered:** sanitized-HTML + `dangerouslySetInnerHTML` (rejected — introduces the project's
first innerHTML render = stored-XSS surface on one sanitizer bug); structured JSON blocks (rejected for C1 —
most net-new work + a content-type migration). Markdown chosen: author-friendly, zero schema change, XSS
contained at a single audited render component.

**Known low-risk consequence (CTO-accepted — NO migration):** existing published announcements hold plain
text in `content`; rendered as Markdown they look near-identical, but stray metacharacters (`*`, `_`, `#`,
leading `>`) may be reinterpreted as formatting. No backfill/escape migration is performed; `remark-breaks`
preserves their newlines.

---

## 2026-06-23 — C2.1 Announcement media presign: scope-mirroring access (security gate before uploads)

**Context:** The `announcements/` presign branch was a stub letting ANY authenticated user presign
(in-code "Intentionally no DB-row requirement yet"). No announcement media exists yet, but the hole
MUST be closed before C2.2 adds uploads, or any logged-in resident could presign any (block/floor-
targeted) announcement file. C2.1 = access control only (no upload endpoint, no media table, no render).

**Key convention (DEFINED now, consumed by C2.2):** `announcements/{announcementId}/{uuid-filename}` —
the announcement id is the FIRST path segment after the prefix, so the gate recovers it from the key
alone and mirrors that announcement's read scope. Single source of the prefix:
`AnnouncementService.MEDIA_KEY_PREFIX` (the old private duplicate in `TicketServiceImpl` was removed).

**Access rule (mirrors the feed scope; analogue of `enforcePhotoAccess`, enforced per-context,
independent of the widened announcement-LIST rule):**
- ADMIN / BOARD_MEMBER → unrestricted (drafts included — authoring preview).
- RESIDENT → allowed IFF the announcement is PUBLISHED and its ALL/BLOCK/FLOOR scope matches one of the
  caller's ACTIVE residencies — the SAME predicate as the feed. A DRAFT's media is never resident-visible.
- Any other role (e.g. TECHNICIAN) → denied.
- Malformed key / nonexistent announcement id → `403 FORBIDDEN`, never a 500.

**Implementation:** `AnnouncementService.assertMediaPresignAccess(objectKey, principalId, role)` parses the
id (malformed → deny), routes staff → allow / resident → scope query / else deny;
`AnnouncementRepository.existsReadableByResident(announcementId, userId)` is a JPQL exists query
(`publishedAt IS NOT NULL` + EXISTS over active residencies with the ALL/BLOCK/FLOOR clause that textually
mirrors `findPublishedForApartment` / `findRecipientUserIds`). The `assertPresignAccess` announcement
branch in `TicketServiceImpl` now delegates to this; `TicketServiceImpl` gains an `AnnouncementService`
dependency (no cycle).

**Alternatives considered:** (a) keep "any authenticated" (REJECTED — leaks block/floor-targeted media);
(b) inline the scope check in `TicketServiceImpl` (REJECTED — the scope rule must live next to the feed
predicate it stays consistent with, guarded by `AnnouncementRecipientConsistencyTest`).

**Tests:** `AnnouncementServiceImplTest` (+8 unit — routing/parse), `AnnouncementMediaPresignAccessTest`
(+6 integration — published/draft/scope through real DB), `PresignPrefixRoutingTest` updated to the new
rule. Full backend suite GREEN (397). `AnnouncementRecipientConsistencyTest` untouched.

---

## 2026-06-23 — C2.2 Announcement media upload (ADMIN, drafts only) on the C2.1-secured presign

**Context:** With the presign hole closed (C2.1), C2.2 adds the actual upload path. CTO rulings: ADMIN-only;
drafts only (published immutable); ≤5 images AND ≤50MB total per announcement (server-enforced in-tx); jpg/png/
webp only validated by Tika on the bytes; kind ∈ {cover, inline} with ≤1 cover. NO render / placeholder / admin
UI (C2.3).

**Media model:** new `announcement_media` table (V21) — `object_key TEXT` (C2.1 convention
`announcements/{announcementId}/{uuid}`), `content_type`, `size_bytes`, `kind` (VARCHAR + CHECK COVER|INLINE),
`original_filename`, `created_by` (auditing via `CreatableEntity`), `created_at`. FK
`ON DELETE CASCADE` to announcements (mirrors `ticket_photos`→`tickets`); the service ALSO collects object keys
before delete to schedule MinIO cleanup, so rows and objects stay consistent. Index on `announcement_id`.

**Cover-replace ruling (chosen REPLACE, not reject):** a second `cover` upload deletes the old cover row in-tx
and schedules the old object for after-commit delete, then saves the new cover. Caps are computed against the
post-replace baseline so replacing a cover never trips the 5/50MB limits.

**Tika-on-bytes:** `TIKA.detect(file.getInputStream())` is the source of truth for the allowed-type check AND the
stored `content_type` AND the key extension — the client `Content-Type`/filename are never trusted. Allowed set
`{image/jpeg, image/png, image/webp}`.

**After-commit object cleanup (NEW mechanism — none existed):** introduced generic, reusable
`ObjectKeysObsoleteEvent` + `ObsoleteObjectCleanupListener`
(`@TransactionalEventListener(phase = AFTER_COMMIT)`) in `common/storage`. Service methods (cover-replace, delete
media, draft delete) publish the event inside their tx; the listener best-effort deletes each object only after
commit (`FileStorageService.delete` already logs+swallows; an orphaned object is harmless and must never roll back
the business op). On rollback the listener never fires. Ticket-photo delete stays in-tx (unchanged); future
surfaces can reuse this event.

**Error codes added:** `ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED` (400), `ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED` (400),
`ANNOUNCEMENT_NOT_DRAFT` (409) — all with VN messages. `AnnouncementService` gained `uploadMedia`/`listMedia`/
`deleteMedia`; `AnnouncementServiceImpl` constructor gained `AnnouncementMediaRepository`, `FileStorageService`,
`ApplicationEventPublisher` (its C2.1 unit-test constructor updated accordingly).

**Alternatives considered:** (a) reject second cover (REJECTED — replace is the better authoring UX, CTO-chosen);
(b) in-tx MinIO delete like ticket photos (REJECTED for media — a storage failure or a rollback would then either
abort the business op or orphan/destroy inconsistently; after-commit is the CTO ruling); (c) trust client
Content-Type (REJECTED — spoofable; Tika on bytes).

**Tests:** `AnnouncementMediaServiceIntegrationTest` (+12, non-transactional to exercise after-commit),
`AnnouncementControllerTest` (+3 security/status). Full backend suite GREEN (412).

---

## 2026-06-28 — Contractor documents = files attach to CONTRACTOR (option C), C3-mechanics

**Decision:** Contract documents are uploaded as a row-per-file list against the **CONTRACTOR** entity
(new table `contractor_document`), reusing the C3 forced-download attachment stack — NOT against the
existing `Contract` entity. This SUPERSEDES the spec'd-but-unbuilt `POST/GET /api/contracts/{id}/attachment`
(API-SPEC §8) and the dormant `contracts.attachment_url` column, which are marked SUPERSEDED (kept
write-idle; NOT dropped).

**Access (staff-only, no resident surface):** ADMIN uploads/deletes; ADMIN+BOARD_MEMBER read/download;
TECHNICIAN and RESIDENT excluded (per §13 R-4). This deliberately diverges from the announcement gate's
resident-readable branch.

**BE P1 as-built (committed `093265b` feat / `6d8c611` test):** migration `V23__create_contractor_document.sql`
(FK contractor ON DELETE CASCADE, creator ON DELETE SET NULL, index on contractor_id); `ContractorDocument`
entity (extends `CreatableEntity`, append-only) + repository (count / sumSizeBytes / findOrdered / dual-key
find); service `uploadDocument`/`listDocuments`/`deleteDocument` + `assertContractorDocumentPresignAccess`;
3 endpoints `POST|GET|DELETE /api/contractors/{id}/documents`. REUSED generic `FileStorageService`
(incl. forced-download `presign(key,disp,type)`), `ContentDispositionUtil`, `MinioConfig` dual client, and the
after-commit `ObjectKeysObsoleteEvent`/`ObsoleteObjectCleanupListener`. Key convention
`contractors/{contractorId}/documents/{uuid}`. Tika magic-byte allowlist {pdf,docx,xlsx,pptx,txt}; caps PER
contractor ≤10MB/file, ≤5 files, ≤50MB total.

**Deliberate divergences from C3 (logged):** (1) `CONTRACTOR_DOCUMENT_TOO_LARGE` → HTTP **413** (C3's
attachment-too-large is 400) so the service-cap and the servlet multipart limit present the FE the same coded
413, and the MockMvc oversize test is deterministic. (2) No draft gate — contractors have no draft/published
lifecycle, so upload is allowed on any existing contractor (404 if missing).

**Reused global 413 handler:** `GlobalExceptionHandler.handleMaxUploadSize` is `@RestControllerAdvice` with a
generic `PAYLOAD_TOO_LARGE` fallback (NOT announcement-coupled) — confirmed global, reused as-is (no STOP).

**Deferred debt (NOT this phase):** cross-module DRY — Tika/caps constants AND the stateless
`detectDocumentMime`/`classifyZipContainer` detection logic duplicate the announcement versions; role-string
extraction duplicated across controllers; OOXML 3× stream re-read; cap TOCTOU (same as C3). Natural home is
`common.storage`. Consistent with prior C3 DRY-debt handling. Logged in `reports/contractor-documents-p1.md`,
not extracted.

**Alternatives rejected:** attach to existing `Contract` entity / new contract record (CTO chose CONTRACTOR);
revive single-key `contracts.attachment_url` (CONTRADICTS C3 row-per-file precedent); resident-readable branch
(CONTRADICTS staff-only ruling); inline preview (CONTRADICTS forced-download ruling). The `/code-review` (high)
"dead gate" finding was REJECTED — the gate is mandated by §13 R-4 + the malformed-key 403 guard.

**Tests:** `ContractorDocumentServiceIntegrationTest` (+11), `ContractorDocumentControllerTest` (+9),
`ContractorServiceImplTest` constructor updated. Full backend suite **457 → 477 GREEN**.

**FE (later phases):** dedicated contractor create/edit pages replacing the modal + a documents manager cloned
from `AnnouncementAttachmentsManager`, with full-parity lazy-save on `/new` (first file pick validates
companyName then auto-creates the contractor).

---

## 2026-06-28 — Contractor documents FE P3a (documents manager on edit page)

**What:** Built `ContractorDocumentsManager` (admin) and mounted it on `ContractorEditPage` ONLY (contractor
id present), as a sibling of the form. Cloned the C3 `AnnouncementAttachmentsManager` mechanics against the P1
endpoints `POST|GET|DELETE /api/contractors/{id}/documents[/{documentId}]`. No BE / auth / schema / API-SPEC
change (P1 endpoints already exist + spec'd). NO `/new` mounting, NO lazy-save (deferred to P3b).

**Decisions / rationale:**
- **Manager owns its list query** (`useContractorDocuments`, key `['contractors', id, 'documents']`) rather
  than receiving the list as a prop. Why: unlike announcements (attachments embedded in the draft detail
  manifest), contractor documents are a SEPARATE GET endpoint — so a self-fetching query + precise
  invalidate-on-mutation (`refetchType:'all'`) is the faithful adaptation. Alt (fold documents into
  `GET /contractors/{id}` detail) rejected: would be a BE/API-SPEC change, out of scope.
- **413 size branch (reaffirming the 2026-06-28 BE sanctioned divergence):** the FE maps ANY `status===413`
  to ONE VN "Tệp quá lớn (tối đa 10MB)" message, covering both `CONTRACTOR_DOCUMENT_TOO_LARGE` (413 service
  cap) and `PAYLOAD_TOO_LARGE` (413 servlet limit). Did NOT copy C3's status-413-only special-case-plus-400-code
  branch. The 400 codes (`TYPE_NOT_ALLOWED`, `LIMIT_EXCEEDED`) route via `getVnErrorMessage`; 3 new
  `CONTRACTOR_DOCUMENT_*` keys added to the shared `@gemek/ui errorMessages.ts` (+ test), parallel to the
  existing `ANNOUNCEMENT_ATTACHMENT_*` keys. Shared-ui infra edit (additive, not the resident app).
- **Boundary-correct `formatSize`:** rounds KB first and promotes to MB if rounding hits 1024, so KB never
  displays ≥1024. The known-buggy admin **announcement `formatSize` boundary bug is LEFT OPEN** (announcement
  module out of scope this phase) — logged, not fixed.
- **BOARD_MEMBER read-access stays API-only this phase** — the edit page is ADMIN-only, so no BOARD FE surface
  was built; deferred FE surface.
- **`/code-review` high (workflow, 23 agents):** 7 verified, **0 correctness bugs**. Applied 4 trivial/
  sibling-aligning fixes (disable upload on isError so the total-size pre-check can't be bypassed against an
  unknown list; use shared `formatVNDate` instead of a local `toLocaleDateString`; single date guard;
  doc-specific success toasts). Deferred 2 (shared-busy cross-disable — verbatim C3 pattern; broad
  `['contractors']` invalidate on contractor save — pre-existing hook, not new P3a code). 6 DRY findings
  refuted (faithful C3 reproduction; consistent with the pre-approved P1 cross-module DRY deferral).

**P1 BE live verification:** STILL deferred to (and now to be discharged by) this P3a CTO :80 browser smoke
(upload/list/download/403/413 vs the running stack + real MinIO).
