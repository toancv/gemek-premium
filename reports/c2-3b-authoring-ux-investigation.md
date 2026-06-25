# C2.3b — Admin authoring UX investigation (READ-ONLY, awaiting CTO ruling)

**Phase:** C2.3b — admin announcement authoring UX.
**Scope of investigation (no code written):** map the current create/edit flow, admin routing, the C2.2 media
contracts, the draft-manifest preview path, the body-editor capability, and the new-draft-id tension. Enumerate
architecture options for a CTO ruling. **No code changed, no pages scaffolded.**

**Predecessor state:** C2.3a (resident safe image render + `announcement-media:{id}` placeholder→manifest + cover
banner) is **CLOSED — CTO-smoked PASS 2026-06-25** (HEAD `4f22989`). C2.2 media upload/list/delete + after-commit
cleanup DONE. This phase builds the ADMIN side that produces the placeholders C2.3a renders.

**Target (PROGRESS backlog item 2):** (1) upload + insert-image that drops `announcement-media:{id}` placeholders
into the body; (2) cover selection; (3) MOVE create/edit out of the current modal to dedicated
`/announcements/new` + `/announcements/:id/edit` pages with a 2-column compose|preview layout (preview = the SAME
safe `@gemek/ui` `MarkdownContent` + a manifest of the draft's uploaded media); (4) wire C2.2 upload/list/delete
into a media manager. Admin app root: `frontend/apps/admin/`.

---

## 1. CURRENT CREATE/EDIT FLOW (the modal)

**Location:** `frontend/apps/admin/src/pages/AnnouncementsPage.tsx`. A single create modal (`AnnouncementsPage.tsx:157-229`);
a separate publish-confirm modal (`:232-245`). **There is NO edit modal and NO edit entry point — the current UI is
create-only.**

### Form fields (exhaustive)

| Field | Control | file:line | Validation |
|---|---|---|---|
| `title` | `<input type=text>` | `:164` | required, non-empty after trim (`:61`) |
| `content` (body) | controlled `<textarea ref>` | `:175` | required, non-empty after trim (`:61`) |
| `type` | `<select>` | `:192` | none; defaults to `TYPES[0]` |
| `targetScope` (state `scope`) | `<select>` | `:196` | if `!= 'ALL'` requires `blockId` (`:62`) |
| `targetBlockId` (state `blockId`) | conditional `<select>` (from `useBlocks()` `:29`) | `:204` | required when scope `!= 'ALL'` (`:62`) |
| `targetFloor` (state `floor`) | conditional `<input type=number>` | `:213` | required + `>=1` when scope `== 'FLOOR'` (`:63`) |
| `publishNow` | `<select>` "true"/"false" string | `:216` | none |
| `sendPush` | **hardcoded `true`** in submit, no UI control | `:72` | — |
| `sendEmail` | **hardcoded `false`** in submit, no UI control | `:73` | — |
| `sendSms` | **hardcoded `false`** in submit, no UI control | `:74` | — |

All validation errors accumulate into `formError` state (`:11`), rendered inline at `:220`.

### Submit flow (create → [publish])

The BE has create, update, publish, delete; the **FE modal only uses create + publish**:

1. **Create draft** — `useCreateAnnouncement()` (`api/hooks.ts:211-217`) → **`POST /announcements`**. Payload:
   `{ title, content, type, targetScope, targetBlockId, targetFloor, sendPush:true, sendEmail:false, sendSms:false }`.
   Called at `AnnouncementsPage.tsx:65`; returns the created announcement incl. `id`. Invalidates `['announcements']` (`:216`).
2. **Publish (only if `publishNow === true`)** — `usePublishAnnouncement()` (`hooks.ts:220-226`) →
   **`POST /announcements/{id}/publish`** (no body). Called at `:78` with `created.id`. Partial-failure handled:
   create OK + publish fail → error shown, draft survives (`:80`). Invalidates `['announcements']` (`:225`).

Publish-from-list (separate path): per-row "Đăng" button (`:136`) sets `pendingPublishId` → confirm modal (`:232`) →
`handleConfirmPublish()` (`:89`) → same `usePublishAnnouncement`.

### Endpoints that EXIST on BE but are NOT wired in the FE modal

- **`PUT /api/announcements/{id}`** — `updateAnnouncement`, `@PreAuthorize hasRole('ADMIN')`, draft-only
  (`AnnouncementController.java:164-173`). **No `useUpdateAnnouncement` hook exists in the admin app.**
- **`DELETE /api/announcements/{id}`** — draft delete, ADMIN (`AnnouncementController.java:188-190`); cascades media
  rows + after-commit object cleanup (C2.2).

→ The `/:id/edit` page can wire to the already-built `PUT`; only the FE hook is missing. No new BE update endpoint needed.

### What would be LOST in a naive modal→page move

- **Cursor-aware toolbar insertion** (`insertMarkdown`, `:37-50`): reads `selectionStart/selectionEnd` (`:40-41`),
  wraps/insets, then restores selection via `requestAnimationFrame` (`:45-49`) against `textareaRef` (`:21`). A naive
  port that drops the ref+RAF logic makes the cursor jump to end on every button — and this is exactly the mechanism
  the new "insert image" needs.
- **`resetForm()` on close** (`:159,:222`) resets `scope/blockId/floor/formError/content`. On a page this becomes
  mount/unmount + back-navigation, not modal toggle.
- **Two-stage publish error recovery** (`:76-82`) — create-then-publish with the "draft survived" message. Must be
  preserved on the new page.
- **Live preview** (`:176-188`) already uses `<MarkdownContent content={content}/>` from `@gemek/ui` (`:4`); import
  survives the move. **But:** today's preview passes NO `mediaManifest`, so it can never render images — see §4.
- **Hardcoded notify flags** (`sendPush:true / sendEmail:false / sendSms:false`, `:72-74`) — currently invisible to
  the admin; preserve as-is unless the move is the moment to surface them (out of scope → keep hardcoded).
- Inline `formError` UI (`:220`), conditional field rendering (`:202-213`), `useBlocks()` data (`:29`) — all carry over
  unchanged.

---

## 2. ADMIN ROUTING

**Route table:** `frontend/apps/admin/src/App.tsx:59-90` (react-router-dom v6, `BrowserRouter` `:61`).
Routes nest under a root `RequireAuth`+`Layout` (`:64`). Announcements route: **`path="/announcements"`**, element
`<AnnouncementsPage/>`, guarded `<RequireRole roles={['ADMIN','BOARD_MEMBER']}>` (`:73`).

- **Deep-linkable param routes already exist:** `path="tickets/:id"` → `TicketDetailPage` (`:71`), which reads
  `const { id } = useParams<{id:string}>()`. This is the precedent to follow for `/announcements/:id/edit`.
- **Adding the new routes** (alongside / replacing `:73`, same role guard):
  - `path="announcements/new"` → `<AnnouncementCreatePage/>`
  - `path="announcements/:id/edit"` → `<AnnouncementEditPage/>`
- **Entry points to redirect to the new pages:**
  - "Tạo thông báo" create button (`AnnouncementsPage.tsx:106`, currently `setShowCreate(true)+resetForm()`) →
    `navigate('/announcements/new')`.
  - There is **no per-row edit button today** (rows `:126-144` only render the publish "Đăng" action `:136`). A new
    per-row "Sửa" → `navigate('/announcements/${id}/edit')` would be net-new (drafts only — published are immutable).

Routes are deep-linkable with params; no sub-route nesting in use.

---

## 3. C2.2 MEDIA ENDPOINTS (exact contracts)

Controller `AnnouncementController.java`; service `AnnouncementServiceImpl.java`; DTO `AnnouncementMediaResponse.java`;
spec `docs/API-SPEC.md` §10 (`:2109-2172`).

### POST /api/announcements/{id}/media  (controller `:263-274`, service `:592-655`)
- Multipart fields (case-sensitive names): **`file`** (binary) + **`kind`**. `kind` value is **case-INsensitive**
  (`kind.trim().toUpperCase()` `:723`) → `COVER` | `INLINE`.
- **Response 201 body = `AnnouncementMediaResponse`** (`AnnouncementMediaResponse.java:26-44`):
  `{ id:UUID, kind, contentType, sizeBytes, originalFilename, objectKey, createdAt }`. Returns `toMediaResponse(saved)` (`:654`).
- **The new media `id` IS returned** (`:26`, API-SPEC `:2128`). → **insert-image can build `announcement-media:{id}`
  directly from the upload response — no extra round-trip.**
- Draft-only (`requireDraftForMedia` `:596`); caps + Tika enforced in-tx (see below).

### GET /api/announcements/{id}/media  (controller `:283-291`, service `:661-669`)
- Returns a JSON **array of `AnnouncementMediaResponse`** (metadata, oldest first), **NO presigned `url`**
  (`AnnouncementMediaResponse.java:17-19`; API-SPEC `:2156` "no presigned URLs"). Authoring/list view.
- **Distinction:** presigned URLs are minted ONLY by the **detail** endpoint `GET /api/announcements/{id}`
  (`buildMediaManifest`), NOT by this list. The media-manager grid would use this list for metadata + the detail
  manifest (or per-key `/api/files/presign`) for thumbnails.

### DELETE /api/announcements/{id}/media/{mediaId}  (controller `:301-311`, service `:676-689`)
- **204 No Content.** Draft-only (`requireDraftForMedia` `:679` → `409 ANNOUNCEMENT_NOT_DRAFT`). Row deleted in-tx
  (`:686`) + object after-commit cleanup (`:687`).

### Constraints (confirmed)
- **Draft-only** upload + delete (`:596`, `:679`).
- **≤5 images** (`MAX_MEDIA_PER_ANNOUNCEMENT=5` `:92`, enforced `:616-619` → `400 ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED`).
- **≤50MB total** (`MAX_MEDIA_TOTAL_BYTES=50MB` `:95`, enforced `:620-623`). Servlet per-file cap 10MB (`application.yml`).
- **Tika on bytes** — `{image/jpeg,image/png,image/webp}` only (`:98-99`, magic-byte detect `:737-748`); client header/ext
  never trusted → `400 ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED`.
- **COVER replace:** uploading a 2nd `cover` deletes the old cover row in-tx + schedules its object cleanup (`:606-630`);
  exactly one COVER survives. Cover-replace adjusts the cap baseline so replacing never trips the 5/50MB caps.

---

## 4. DRAFT MANIFEST FOR PREVIEW

`GET /api/announcements/{id}` builds the manifest via `buildMediaManifest(announcement, principalId, role)`
(`AnnouncementServiceImpl.java:273-293`), gated by `assertMediaPresignAccess` (`:298-326`).

- **ADMIN/BOARD = unrestricted** (`:310-311`) → an **ADMIN viewing a DRAFT gets a NON-EMPTY manifest**. Proven by
  `AnnouncementDetailMediaManifestTest.adminDraft_manifestPresent` (`:147-156`). This is the preview's data source.
- **BE manifest item shape:** `AnnouncementResponse.MediaRef { id:UUID, kind:COVER|INLINE, url }` (`AnnouncementResponse.java:108-118`);
  `url` is a fresh presigned GET (10-min expiry) minted per request (`:290`). Detail-only (list/create/update/publish
  carry empty `media`). API-SPEC `:2026-2053`.
- **Renderer prop shape (C2.3a):** `MarkdownContent` takes `mediaManifest?: AnnouncementMediaManifestEntry[]` where each
  entry is **`{ mediaId, kind, url }`** (`packages/ui/src/components/MarkdownContent.tsx:27-34`). ⚠ **Field-name mismatch:**
  the BE `MediaRef` key is `id`, the renderer prop key is `mediaId` — the preview wiring must map `id → mediaId`. (The
  resident page already does this mapping; the admin preview must do the same.)
- **Refetch requirement:** after each upload/delete the page must **re-fetch `GET /api/announcements/{id}`** so a new
  manifest entry (fresh presigned URL) exists before the preview can resolve the just-inserted placeholder. The COVER
  entry is rendered as a banner by the page, not inline (C2.3a convention).

---

## 5. BODY EDITOR + PLACEHOLDER INSERTION

- **Editor = plain controlled `<textarea>`** (`AnnouncementsPage.tsx:175`), NOT a rich/WYSIWYG editor. Markdown is
  authored as text; C1 added a 5-button format toolbar (`:169-173`: Đậm/Nghiêng/Tiêu đề/Danh sách/Liên kết).
- **Cursor insertion already works:** `insertMarkdown(before, after, placeholder)` (`:37-50`) reads
  `selectionStart/selectionEnd`, splices into `content`, and restores the selection via `requestAnimationFrame` +
  `textareaRef`. The link button already inserts `[label](url)` at the cursor.
- **Simplest viable insert-image mechanism:** reuse this exact pattern — on upload success, call (an analogue of)
  `insertMarkdown('![', '](announcement-media:'+id+')', 'mô tả ảnh')`, or a one-shot insert of
  `![alt](announcement-media:{id})` at the cursor. **No editor swap required** — the existing textarea + ref already
  supports cursor insertion. (A heavy editor would be over-engineering and would also re-open the XSS surface C1/C2.3a
  carefully bounded.)

---

## 6. THE NEW-DRAFT-ID TENSION (key ruling)

Media upload is **draft-only and needs an `announcementId`** (`POST /announcements/{id}/media`), but `/announcements/new`
has **no id yet**. A minimal draft (`CreateAnnouncementRequest`) requires **`title`, `content`, `type`, `targetScope`**
all non-blank/non-null (`CreateAnnouncementRequest.java:31-45`) — so a draft cannot be created with truly empty fields.
**There is NO orphan/draft-cleanup mechanism** — no `@Scheduled`/cron for announcements (schedulers exist only for
contracts/tickets); abandoned drafts persist until an ADMIN deletes them via `DELETE /announcements/{id}`. So any option
that auto-creates drafts accumulates orphans with no janitor.

`/:id/edit` already has an id (and the BE `PUT` + media endpoints all work on it) → the split matters: **edit is
unconstrained** (upload immediately); the tension is **only on `/new`**.

### Option (a) — auto-create an empty DRAFT on entering `/new`
- On mount, `POST /announcements` with placeholder/minimal fields → get `id` → immediately redirect to `/:id/edit` (or
  stay, holding the id) → uploads attach from the first keystroke.
- **UX:** smoothest — upload/insert/cover available instantly, single code path (everything is "edit a draft").
- **Orphan risk:** HIGH — every abandoned `/new` (back button, tab close) leaves a junk draft, and there is **no cleanup
  job**. Also must satisfy the 4 `@NotBlank/@NotNull` fields with dummy values (e.g. "Untitled") → junk drafts are even
  visible in the list.
- **Interaction:** collapses `/new` into `/:id/edit`; `/new` becomes a thin "create id then redirect" shim.

### Option (b) — gate media behind a first "Save draft"
- `/new` shows compose form with media manager **disabled**; admin fills title/content/type/scope → "Lưu nháp" →
  `POST /announcements` → redirect to `/:id/edit` with media now enabled.
- **UX:** one extra explicit click before images; matches the BE's "draft-only" reality honestly; the 4 required fields
  are naturally satisfied (the admin typed them).
- **Orphan risk:** LOW — a draft exists only after a deliberate save; abandoning `/new` before save creates nothing.
- **Interaction:** clean `/new` (compose+save) vs `/:id/edit` (full compose+preview+media) split; the two pages differ
  by exactly "media enabled". Simplest to reason about; recommended-shaped given no cleanup job exists.

### Option (c) — stage uploads client-side, attach after first save
- Hold selected files in browser memory; on first save, `POST /announcements` then replay each `POST .../media`,
  then rewrite the staged placeholders with the real returned ids.
- **UX:** feels seamless (upload before save) but the preview can't show real images until attach (no manifest/presigned
  URL until the row exists) → would need blob-URL previews + a placeholder-rewrite step.
- **Orphan risk:** LOW for drafts (nothing created until save) — but adds client complexity: temp-id→real-id rewriting,
  blob preview vs presigned preview divergence (two render paths = re-opens the surface C2.3a unified), partial-failure
  on replay.
- **Interaction:** most code; diverges the preview from the single safe-manifest path. Highest risk for least durable gain.

---

## 7. OPEN RULINGS FOR CTO

1. **New-draft-id strategy — (a) auto-create / (b) save-first-then-media / (c) client-stage.** Given there is **no
   orphan-draft cleanup** and the 4 required draft fields, (b) has the lowest orphan + lowest complexity; (a) is smoothest
   UX but accumulates junk drafts with no janitor; (c) re-introduces a second (blob) render path. **Pick one.**
2. **Modal: remove fully, or keep as fallback?** Recommend removing once `/new` + `/:id/edit` land (one authoring
   surface, no drift), but CTO may want the modal retained transitionally.
3. **Cover-selection UX** — choose `kind` at upload time (a "Đặt làm ảnh bìa" toggle in the upload control) vs a
   per-item "set as cover" toggle on an already-uploaded item. Note C2.2 cover-replace already guarantees exactly one
   COVER (2nd cover deletes the 1st in-tx), so a per-item toggle is safe and reversible. **Pick the interaction.**
4. **Placeholder insertion mechanism** — reuse the existing cursor-aware `insertMarkdown` (ref+RAF) to drop
   `![alt](announcement-media:{id})` at the cursor on upload success. Confirm no rich-editor swap (keeps the C1/C2.3a
   XSS bounds).
5. **Fields/behaviour at risk in the move** (call out so none are dropped): cursor-restore RAF logic; two-stage
   create→publish error recovery; `resetForm`→navigation lifecycle; the hardcoded `sendPush/Email/Sms` flags (keep
   hardcoded unless CTO wants them surfaced); preview must now pass `mediaManifest` (today it passes none) with the
   `id→mediaId` field mapping; preview must refetch detail after each upload/delete.

### Phase breakdown — PROPOSAL (pending ruling, not committed)

- **P1 — routing + move (no behaviour change):** add `/announcements/new` + `/announcements/:id/edit` routes + pages;
  port the existing modal form (all fields, validation, toolbar incl. cursor logic, two-stage publish, 2-col
  compose|preview) 1:1; add a `useUpdateAnnouncement` hook wired to the existing `PUT /announcements/{id}`; redirect the
  create button + a new per-row "Sửa" (drafts only) to the pages; retire or keep the modal per ruling #2. No media yet.
- **P2 — media manager + upload/insert:** wire `POST/GET/DELETE .../media`; upload control with Tika-allowed types +
  caps surfaced; insert-image drops `announcement-media:{id}` at the cursor from the upload response id; cover selection
  per ruling #3. Gated on the new-draft-id ruling (#1).
- **P3 — preview pane + cover:** preview consumes the detail `media` manifest (map `id→mediaId`), refetch-after-upload;
  COVER rendered as banner, inline placeholders resolve in body — same safe `MarkdownContent` as the resident side.

---

## Facts vs unknowns

All §1–§6 claims are file:line-cited against HEAD `4f22989`. No `[TODO: kiểm tra]` outstanding — the one risk flagged
(`id` vs `mediaId` field-name mismatch between BE `MediaRef` and the renderer prop) is confirmed, not assumed. No code
written, no pages scaffolded; this report stops for the CTO ruling on §7.

---

## P1.5 dev-DB smoke (agent-run, 2026-06-25)

CTO-acceptance: exercised the REAL `PUT /api/announcements/{id}` over nginx :80 (ADMIN `0901100001`), read back
the persisted rows from dev DB `gemek` (user `gemek`, NOT `gemek_test`) via `psql`. Block used:
`c801d08e-abd0-4c64-b2e6-6ff04b4b851f`. Columns: `scope`, `target_block_id`, `target_floor`. **All 5 PASS.**

| # | Case | HTTP | DB `scope \| target_block_id \| target_floor` | Expected | Verdict |
|---|------|------|----------------------------------------------|----------|---------|
| 1 | create BLOCK+block → PUT scope=ALL | 200 | `ALL \| NULL \| NULL` | ALL,NULL,NULL | ✅ stale block+floor cleared |
| 2 | create FLOOR+block+floor7 → PUT scope=BLOCK | 200 | `BLOCK \| c801d08e… \| NULL` | BLOCK,block,NULL | ✅ floor nulled, block kept |
| 3 | create ALL → PUT scope=BLOCK+block | 200 | `BLOCK \| c801d08e… \| NULL` | BLOCK,block,NULL | ✅ block set |
| 4 | case-2 draft → PUT title/content only (scope=BLOCK, no block resent) | **200** | `BLOCK \| c801d08e… \| NULL` (unchanged) | 200, no 404 | ✅ reuse-existing block, no re-fetch |
| 5 | create ALL → PUT scope=BLOCK, NO block | **400** | row unchanged `ALL \| NULL \| NULL` | 400 VALIDATION, not 500 | ✅ `{"error":"VALIDATION_ERROR","message":"targetBlockId required for BLOCK scope."}` |

Raw: `scratchpad/p15-smoke.out`. Conclusion: scope-derived target normalization is correct at the DB level
end-to-end; the content-only-edit no-refetch path and the BLOCK-without-block parity (400 not 500) both hold.
**P1.5 ACCEPTED — ready for P2.**
