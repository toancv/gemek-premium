# Announcements ("Tin tức") rich-content — current-state investigation

Read-only survey of the announcements feature to inform a CTO ruling on rich content (rich text + images + file
attachments, ADMIN-authored) BEFORE any cluster is coded. NO code changed. Evidence is file:line. Options are
presented WITHOUT deciding them (final section).

## Constraints from DECISIONS (must be honored by any design)
- **Terminology locked:** announcements = **"Tin tức"** everywhere (nav/home/page/title); the notification bell is
  "Thông báo" — never swap (DECISIONS 2026-06-10). Confirmed in resident i18n (`apps/resident/src/i18n/vi.ts`
  `news/announcements/title = 'Tin tức'`).
- **Multi-residency feed (P1):** the resident feed is DISTINCT-by-announcement-id and scoped to the user's ACTIVE
  apartments (ALL of them), not one. Any rich-content change must NOT regress this.
- **Publish is atomic + dispatches notifications** (DECISIONS 2026-06-11): `publishAnnouncement` creates in-app
  notification rows inside the publish tx (CAS guard, already-published → 409). Rich content must not break the
  feed↔dispatch scope-consistency contract (`AnnouncementRecipientConsistencyTest`).
- **Presign hardened to 10 min** and media access is **per-context** (mirrors `enforcePhotoAccess`, independent of
  any widened announcement-read rule) — see §D. Announcement media MUST mirror the announcement's own scope, not
  invent an ad-hoc rule.

---

## A. Current announcement model

- **Table** `V7__create_announcements.sql` (`backend/src/main/resources/db/migration/V7__create_announcements.sql:12-36`):
  `id uuid`, `title varchar(255)`, **`content TEXT`** (unbounded), `type` (enum), `scope`/`target_scope` (enum),
  `target_block_id uuid` (nullable FK), `target_floor smallint` (nullable), `published_at timestamptz` (nullable —
  **null = draft**), `created_at`/`updated_at`, `created_by_user_id uuid` (FK, author). Later `V18` added
  `updated_by` (auditing convergence).
- **Entity** `Announcement.java:57-59`: `@Column(name="content", nullable=false, columnDefinition="TEXT")
  private String content;` — plain unbounded `String`, **no format metadata** (no "content_type"/"format" column).
- **Editable after publish? NO.** `AnnouncementServiceImpl.updateAnnouncement` (`:260-263`):
  `if (announcement.getPublishedAt() != null) throw INVALID_STATUS_TRANSITION "Cannot edit a published
  announcement."` → only DRAFTS are editable. Consequence: **no re-notification problem** (you cannot edit a
  published announcement; publish is the one-way dispatch event). Drafts have no recipients yet.

## B. Create/edit path + permission

- **Create** `POST /api/announcements` — `AnnouncementController.java:111` `@PreAuthorize("hasRole('ADMIN')")`,
  DTO `CreateAnnouncementRequest`. **Update** `PUT /api/announcements/{id}` — `:161` `@PreAuthorize("hasRole('ADMIN')")`,
  DTO `UpdateAnnouncementRequest`. **Author = ADMIN-only today** (matches the CTO target; not wider). (Read/list is
  widened to ADMIN+BOARD for the admin app + RESIDENT feed; authoring is ADMIN-only.)
- **Validation/sanitization on write:** `CreateAnnouncementRequest.java:32-36` — `@NotBlank title`,
  `@NotBlank content`. `UpdateAnnouncementRequest` fields nullable, no constraints. **NO sanitization/escaping of
  `content` on write** — it is stored verbatim. (Safe TODAY only because it is rendered as escaped plain text — §C.
  The moment content is rendered as HTML/markdown, write-time content becomes an injection vector.)

## C. Resident read/render path (the XSS surface)

- **Feed** `GET /api/announcements` (`AnnouncementController.java:80-94`) → `AnnouncementServiceImpl:104-135`:
  `residentRepository.findAllActiveByUserId(principalId)` then `publishedForResidenciesSpec()` with
  **`query.distinct(true)` (`:163`)** + a per-residency ALL/BLOCK/FLOOR predicate. **P1 multi-residency intact** —
  DISTINCT-by-id across the user's active apartments. The feed predicate mirrors the dispatch recipient query
  (`ResidentRepository.findRecipientUserIds`), guarded by `AnnouncementRecipientConsistencyTest`.
- **Render (XSS surface) — currently SAFE:**
  - Feed list `apps/resident/src/pages/AnnouncementsPage.tsx:34` renders TITLE only:
    `<h3 ...>{a.title}</h3>` (React text node, auto-escaped). No body in the list.
  - Detail `apps/resident/src/pages/AnnouncementDetailPage.tsx:41`:
    **`<p className="text-sm text-gray-700 whitespace-pre-line">{announcement.content}</p>`** — React text node →
    auto-escaped; `whitespace-pre-line` preserves author newlines. **No `dangerouslySetInnerHTML` anywhere in the
    codebase; zero HTML/markdown rendering.** So today there is NO XSS surface — and NO formatting.
  - Data flow: `useAnnouncement(id)` → `GET /announcements/{id}` → `AnnouncementItem.content: string` →
    `{announcement.content}`. Read-marking fires on detail mount (`useMarkAnnouncementRead`).
- **Admin authoring UI:** `apps/admin/src/pages/AnnouncementsPage.tsx:142` plain
  `<textarea name="content" rows={4} ...>` — **plain text, no rich editor**. **No edit-after-publish UI** (table
  only offers "Đăng"/publish for drafts), consistent with the BE immutability rule.

## D. Media infrastructure (images + attachments)

- **Storage + presign is GENERIC** (not ticket-only): `FileStorageService.java` — `upload(objectKey, stream,
  contentType, size)` (`:66`), `presign(objectKey)` (`:89`); **presigned-GET expiry = 600 s = 10 minutes**
  (`:40 PRESIGN_EXPIRY_SECONDS = 600`). DB stores object KEYS (not URLs). MinIO backend.
- **Access control is per-context, prefix-routed:** `FileController` presign gate (`:56-68`) →
  `assertPresignAccess(objectKey, principal)`; routed by key prefix. Ticket photos
  (`TICKET_KEY_PREFIX = "tickets/"`, `TicketServiceImpl.java:80`) enforce `enforcePhotoAccess` (`:901-919`):
  RESIDENT must `existsActiveByUserIdAndApartmentId` of the ticket's apartment; TECHNICIAN assigned-or-NEW;
  ADMIN/BOARD unrestricted. **This per-context rule is independent of the widened announcement-READ rule** (the
  security hardening DECISIONS note) — i.e. media access is enforced on its own, not inferred from list visibility.
- **Announcement media — what already exists vs net-new:**
  - **Reserved but STUBBED:** `ANNOUNCEMENT_KEY_PREFIX = "announcements/"` already exists
    (`TicketServiceImpl.java:83`, "public-read surface"). The presign gate for that prefix is a **stub** that lets
    **ANY authenticated user** presign, with an in-code note "Intentionally no DB-row requirement yet" — i.e. it
    does **NOT** yet scope to the announcement's block/floor. ⚠️ A rich-content design MUST replace this stub with
    a scope-mirroring check (see open questions) before shipping announcement media, or it is an access-control
    hole (any logged-in resident could presign any announcement file regardless of scope).
  - **Net-new needed for announcements:** (1) a media table (e.g. `announcement_media(id, announcement_id FK,
    object_key, content_type, size, kind=cover|inline|attachment, created_at)`) — today the entity has NO media
    relation; (2) an upload endpoint (e.g. `POST /api/announcements/{id}/media`, ADMIN, multipart, drafts only);
    (3) the scoped `assertPresignAccess` for the `announcements/` prefix; (4) response DTO carrying presigned URLs
    (mirror the ticket-photo manual-mapping pattern — MapStruct can't presign).
  - **Access-scope rule to MIRROR (not invent):** an announcement's image/file is viewable by exactly whoever can
    READ that announcement per its ALL/BLOCK/FLOOR scope — reuse the SAME predicate as the feed
    (`publishedForResidenciesSpec` / `findRecipientUserIds`) so media visibility == announcement visibility, plus
    ADMIN/BOARD unrestricted. This is the direct analogue of `enforcePhotoAccess`.

## E. Format / sanitization library landscape (inventory only — nothing added)

- **Backend** (`pom.xml`): **Apache Tika 2.9.1** present — MIME-type detection only, NOT a sanitizer. **No** HTML
  sanitizer (no `jsoup`, no `owasp-java-html-sanitizer`), **no** Markdown lib (no `commonmark`/`flexmark`).
- **Frontend** (all `package.json`): **NONE** of `dompurify`, `marked`, `markdown-it`, `react-markdown`,
  `sanitize-html`, `slate`, `tiptap`, `quill`, `draft-js`. Apps carry only React 18.3.1 / React-Router 6.26.2 /
  TanStack Query 5.56.2 / Zustand / Axios / Tailwind. **No sanitization or rich-render infra exists** on either
  side — any chosen format brings a new dependency.

---

## Open questions for CTO (decisions to make BEFORE coding — presented, not decided)

### 1. Content format + XSS handling (pick one)
- **(a) Markdown stored, rendered sanitized.** Store raw markdown in `content` (TEXT, no schema change); render
  via a markdown→HTML lib + sanitizer. *Tradeoff:* author-friendly, smallest data change; XSS risk lives entirely
  at render — MUST sanitize the generated HTML (raw markdown allows embedded HTML/`javascript:` links). Adds
  FE deps (e.g. a markdown renderer + DOMPurify) and/or a BE renderer.
- **(b) Sanitized-HTML stored.** Accept HTML, sanitize on write (allowlist), store clean HTML; render with
  `dangerouslySetInnerHTML`. *Tradeoff:* render is simple, but introduces the project's FIRST
  `dangerouslySetInnerHTML` — one sanitizer bug = stored XSS; needs a BE sanitizer (jsoup/OWASP) + strict allowlist
  and ideally re-sanitize on render too.
- **(c) Structured JSON blocks.** Store a constrained block model (paragraph/heading/list/image/link) as JSON;
  render each block type explicitly (no raw HTML). *Tradeoff:* safest (no HTML execution path), but most net-new
  work (editor + schema + renderer) and a `content` type/migration change.

### 2. Sanitize on write, on render, or both?
- Write-only is fragile (a render that trusts "already clean" data breaks if any other path inserts content).
  Render-only re-sanitizes every read (cost). **Defense-in-depth = sanitize on write AND escape/sanitize on
  render.** CTO to set the policy; it determines where the new dependency lives (BE, FE, or both).

### 3. Media: reuse presign infra (yes) + the access rule
- Reuse the existing MinIO/object-key/10-min-presign infra (generic, proven). Net-new = `announcement_media`
  table + ADMIN upload endpoint (drafts only) + **replace the any-authenticated `announcements/` presign stub**
  (`TicketServiceImpl.java:~808-813`) with a scope-mirroring check (media viewable iff caller can read the
  announcement per its block/floor scope; ADMIN/BOARD unrestricted). CTO to confirm: media scope == announcement
  scope (recommended, mirrors `enforcePhotoAccess`) vs a simpler "any authenticated resident" (rejected — leaks
  block/floor-targeted media).

### 4. Edit-after-publish + orphan cleanup
- Today published = immutable, so there is no post-publish media-orphan problem. Two sub-questions: (i) keep
  published immutable (simplest — edit = create new), or allow editing published content/media (then must decide
  re-notification + orphan deletion of replaced objects)? (ii) for DRAFTS, replacing/removing media should delete
  the orphaned MinIO object — needs a cleanup path (on media-row delete and on draft delete). CTO to rule on
  whether published stays immutable.

### 5. Proposed cluster split (for ordering approval — not started)
- **C1 — rich TEXT + XSS-safe render, NO MinIO.** Implement the chosen format (Q1) + sanitization policy (Q2);
  admin editor + resident sanitized render; preserves DISTINCT multi-residency feed + "Tin tức" terminology. Self
  contained, no storage changes. *Lowest risk, highest value first.*
- **C2 — cover/inline images.** `announcement_media` (kind=cover|inline) + ADMIN upload (drafts) + scoped presign
  (Q3) + resident render of images via presigned URLs.
- **C3 — file attachments.** Same infra, kind=attachment + download UX + Tika content-type validation on upload.
- CTO to approve this ordering (C1 → C2 → C3) or re-slice.

No fixes, no code, no library additions made.
