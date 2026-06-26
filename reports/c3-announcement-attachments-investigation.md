# C3 — Announcement file ATTACHMENTS (downloadable documents) — INVESTIGATION ONLY

**Type:** INVESTIGATE-ONLY (read-only, cite `file:line`). No code/schema change. For CTO ruling.
**Date:** 2026-06-26 **HEAD:** `89df3aa` (C2.3b CLOSED — close-out `38e7196`/`2a3f4b0`, post-fixes `886365e`/`81ac27c`/`6e41cd4`, layout `d4e99bd`).
**Scope:** downloadable document attachments on announcements (PDF/docx/etc.), **distinct** from the C2.x inline/cover *images* (which are body-placeholders / banner). Map the reusable media infra, surface the security model for serving arbitrary documents, enumerate architecture + type + caps options.

> **C2.3b closed confirmed** — media authoring UX fully landed; this report opens C3 and changes nothing.

---

## STEP 1 — Existing media infra (the reuse baseline)

### 1.1 `announcement_media` table (Flyway V21 + entity)
`backend/src/main/resources/db/migration/V21__create_announcement_media.sql:17-36`:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `DEFAULT gen_random_uuid()` (`:18`, PK `:27`) |
| `announcement_id` | UUID NOT NULL | FK → `announcements(id)` **`ON DELETE CASCADE`** (`:28-29`, mirrors `ticket_photos`→`tickets`) |
| `object_key` | TEXT NOT NULL | C2.1 key convention `announcements/{announcementId}/{uuid}` (`:20`) |
| `content_type` | VARCHAR(100) | the **Tika-detected** type, stored at upload (`:21`) |
| `size_bytes` | BIGINT | feeds the 50 MB total cap (`:22`) |
| `kind` | VARCHAR(20) NOT NULL DEFAULT `'INLINE'` | `CHECK (kind IN ('COVER','INLINE'))` (`:23`, `:33`) |
| `original_filename` | VARCHAR(255) | display only (`:24`) |
| `created_by` | UUID | FK → `users(id)` `ON DELETE SET NULL` (`:25`, `:30-31`) |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | `:26` |

Index `idx_announcement_media_announcement_id` (`:36`). Entity: `backend/.../module/announcement/entity/AnnouncementMedia.java`; kind enum `AnnouncementMediaKind.java` (`COVER`/`INLINE`).

### 1.2 Media endpoints (controller)
`backend/.../module/announcement/AnnouncementController.java`:
- **POST** `/api/announcements/{id}/media` (`:263-273`) — `consumes=MULTIPART_FORM_DATA`, multipart fields `file` + `kind` (case-insensitive `cover`/`inline`), draft-only, `@PreAuthorize` ADMIN; **201** with `AnnouncementMediaResponse` (metadata only — **no presigned URL** in the create response).
- **GET** `/api/announcements/{id}/media` (`:283-290`) — ADMIN/BOARD, list metadata oldest-first.
- **DELETE** `/api/announcements/{id}/media/{mediaId}` (`:301-309`) — draft-only, ADMIN.

Service (`AnnouncementServiceImpl.java`): `uploadMedia` (`:620-682`), `listMedia` (`:689-696`), `deleteMedia` (`:704-716`); draft-delete cascade collects keys then schedules after-commit cleanup (`:600-608`). Response DTO `toMediaResponse` (`:811-820`) returns `id, kind, contentType, sizeBytes, originalFilename, objectKey, createdAt`.

### 1.3 Presign — dual MinioClient + nginx front
- **`MinioConfig.java`**: `@Primary minioClient()` built against internal `endpoint` (byte ops, `:71-79`); `minioPresignClient()` built against `publicEndpoint` (browser-reachable host; falls back to internal when unset, `:89-100`). `region` pinned `us-east-1` (`:62`) so presign stays **offline** SigV4 (no GetBucketLocation call to the unreachable public host).
- **`FileStorageService.java`**: `upload()`→internal client `putObject` (sets the object's stored `.contentType(contentType)`, `:72-86`); `presign()`→**public** client `getPresignedObjectUrl` GET, expiry `PRESIGN_EXPIRY_SECONDS=600` (`:41`, `:95-108`); `delete()`→internal (`:117-127`). Clients `@Qualifier`-wired in the ctor (`:54-56`).
- **nginx front** (`nginx/nginx.conf:93-107`): `server listen 8090` → `proxy_pass http://minio:9000` with `proxy_set_header Host $http_host` (FULL host **incl. port** — `$host` would drop the port and mismatch `SignedHeaders=host` → 403). Raw 9000 deliberately unpublished.

### 1.4 Access gate (who may mint a URL)
- **`AnnouncementServiceImpl.assertMediaPresignAccess`** (`:299-325`): parses the announcement id from the object key (malformed → 403, `:303-305`); ADMIN/BOARD bypass (draft authoring, `:309-318`); RESIDENT → single `existsReadableByResident` JPQL that **also enforces published-only** + ALL/BLOCK/FLOOR scope mirror of the feed (`:321-324`); any other role → 403. Key-id parse: `parseAnnouncementId` (`:339-342`).
- **`buildMediaManifest`** (`:273-293`): loads rows, gates **once** on `rows.get(0)` (all rows of one announcement share its scope, `:282`), then maps each row to a **fresh** presigned URL (`:287-290`). Denial → **empty manifest** (no leak, no 500). Wired into the detail path only (`:255-257`); list/mutation responses carry no manifest (`:1001`). External entry: `TicketServiceImpl.assertPresignAccess` routes the `announcements/` prefix here (per C2.1, `reports/c2-1-announcement-presign-access.md:34-40`).

### 1.5 Caps + type validation + cleanup
- Caps (`AnnouncementServiceImpl.java`): `MAX_MEDIA_PER_ANNOUNCEMENT=5` (`:92`), `MAX_MEDIA_TOTAL_BYTES=50MB` (`:95`), enforced in-tx with cover-replace baseline adjustment (`:641-650`).
- Type: `ALLOWED_MEDIA_MIME_TYPES={image/jpeg,image/png,image/webp}` (`:98-99`), `TIKA` magic-byte detect on bytes (`:767-770`), extension picked from detected MIME `extensionFor` (`:786-788`). Client header/extension never trusted.
- Servlet caps (`application.yml:25-28`): `max-file-size: 10MB`, `max-request-size: 55MB`. (413 has no app error code — FE special-cases it, `AnnouncementMediaManager.tsx:67-70`.)
- Cleanup: `ObjectKeysObsoleteEvent` + `@TransactionalEventListener(AFTER_COMMIT)` best-effort delete (`common/storage`; described `reports/c2-2-announcement-media-upload.md:47-51`).

### 1.6 Spec precedent (NOT a code precedent)
`docs/API-SPEC.md:1608-1639` already specs **contract attachments** — `POST/GET /api/contracts/{id}/attachment`, PDF-only ≤20 MB, presigned **download** URL — but both are marked **⚠️ NOT IMPLEMENTED** (`:1610`, `:1626`) with the R-4 staff-only presign gate requirement (`:2438`). So a *document-attachment shape* is already imagined in the spec, but no serving/Content-Disposition code exists to copy.

---

## STEP 2 — Security of serving arbitrary documents (the crux)

### 2.1 Current serving = INLINE, no Content-Disposition
`FileStorageService.presign()` (`:95-108`) builds `GetPresignedObjectUrlArgs` with **only** bucket/object/method/expiry — **no `Content-Disposition`, no `extraQueryParams`**. So MinIO serves the object with its **stored content-type** (set at `putObject` `.contentType(...)`, `:78`) and **no disposition header** → the browser renders **INLINE** by default. Today this is harmless because only `{jpeg,png,webp}` are storable (`:98-99`), all of which render as benign images.

### 2.2 Forcing download IS possible (SDK 8.5.9)
The MinIO Java SDK 8.5.9 `GetPresignedObjectUrlArgs.builder()` (via `BaseArgs.Builder.extraQueryParams(Map<String,String>)`) supports S3 response-override query params that are **folded into the signature**:
- `response-content-disposition=attachment; filename="<original>"` → forces a download regardless of stored content-type.
- `response-content-type=application/octet-stream` → overrides the served Content-Type.

Both ride in the signed query string, so a tamper attempt invalidates the signature. **This is the recommended serving path for documents** — it requires a `presign()` overload accepting these params (currently `presign(objectKey)` takes none).

### 2.3 nginx front sets NO `nosniff` / CSP
`nginx/nginx.conf:93-107` (the :8090 block) sets **no** `X-Content-Type-Options: nosniff`, **no** `Content-Security-Policy`, no `Content-Disposition`. Consequence: an uploaded `text/html` or `image/svg+xml` served **inline** from `:8090` **executes script** in the browser (HTML `<script>`, SVG `<script>`/`onload`). The image caps protect today only because Tika blocks non-image bytes — relaxing the type set without changing serving posture would open this.

### 2.4 Origin relationship + XSS blast radius
- Dev origins: admin `localhost:80`, resident `localhost:81`, MinIO-front `localhost:8090`. **Port is part of the origin → all three are CROSS-ORIGIN to each other.** Prod MinIO delivery is a **[PLANNED]** subdomain (DECISIONS 2026-06-25 "prod object delivery … [PLANNED]") → also cross-origin to the app hosts.
- Implication: an inline-served HTML/SVG attachment runs script in the **`:8090` (object-host) origin**, *not* the admin/resident app origin. The Same-Origin Policy therefore prevents it from reading the app's `localStorage`/JWT or app cookies directly.
- **But the blast radius is still real:** (a) it is **stored XSS hosted on a company-trusted host/subdomain** — phishing, drive-by redirects, credential-harvest forms that look first-party; (b) script in the object origin can issue same-origin requests to MinIO and read any object the victim's browser can reach via an unexpired presigned URL it can observe/guess in that tab; (c) if prod ever places the object host on a **cookie-sharing parent domain** of the app (e.g. `media.app.com` vs `app.com` with a `.app.com`-scoped cookie), the cross-origin protection narrows further. `[TODO: kiểm tra]` the exact prod subdomain + cookie scope once the [PLANNED] delivery host is decided.

### 2.5 Minimum safe serving posture (RECOMMENDATION — not applied)
For documents, serve with **all** of:
1. **Force `Content-Disposition: attachment; filename="<original>"`** via the signed `response-content-disposition` param (§2.2) — download, never inline-render.
2. **`response-content-type=application/octet-stream`** (or the true type) so a mislabeled object can't be sniffed into an executable type.
3. **Add `X-Content-Type-Options: nosniff`** on the :8090 nginx front (defense-in-depth; cheap, global).
4. **Block renderable/script-capable types at upload** (`text/html`, `image/svg+xml`, anything browser-executable) via Tika magic-byte allow-list — even with forced download, never store them.
With (1)+(4) the inline-execution surface is closed at both the serving and the storage layer.

---

## STEP 3 — Architecture options (reuse vs parallel — do NOT pick)

### (A) EXTEND `announcement_media` — add an `ATTACHMENT` kind
Add `'ATTACHMENT'` to the `kind` CHECK + enum; reuse the same table, endpoints, presign, gate, cleanup.
- **Migration shape:** `Vxx` ALTER the `chk_announcement_media_kind` CHECK to `IN ('COVER','INLINE','ATTACHMENT')`; **no column drop**. Attachments need a display filename — `original_filename` already exists (`:24`), reuse it. No new FK (CASCADE already correct).
- **Endpoint/DTO:** the existing POST/GET/DELETE `.../media` already accept a `kind` field — POST works as-is if `parseKind` (`:746-751`) accepts `ATTACHMENT`. But the **caps + type validation are kind-blind today** (`detectMime` (`:767`) hard-codes the image set; the count/byte caps lump all kinds) → must branch by kind: attachments get the document type set + their own caps, images keep `{jpeg,png,webp}`. Detail manifest `buildMediaManifest` would now mint download-disposition URLs for ATTACHMENT rows (needs the §2.2 presign overload) and inline-render URLs for image rows.
- **Semantics caveat:** attachments are **NOT body-placeholders** and have **no cover/inline meaning** — they're a flat downloadable list. Folding them into a table whose `kind` otherwise means "where in the body" muddies the model; the FE must filter `kind==ATTACHMENT` out of the image grid and into a separate list.
- **Effort:** low (one CHECK alter + kind-branched validation + presign overload). **Risk:** the shared caps/validation path gets `if kind` branches — easy to get the cap accounting wrong (images and docs sharing `countByAnnouncementId`).

### (B) NEW `announcement_attachment` table + parallel endpoints
Separate table + `POST/GET/DELETE /api/announcements/{id}/attachments`, reusing `FileStorageService`/presign/`assertMediaPresignAccess`/cleanup, with its own caps/types/list.
- **Migration shape:** new table mirroring V21 (`id`, `announcement_id` UUID NOT NULL FK **ON DELETE CASCADE** per precedent, `object_key` TEXT, `content_type`, `size_bytes`, `display_filename` VARCHAR(255), `created_by` FK SET NULL, `created_at`), index on `announcement_id`. **No drops.** Object key convention can stay `announcements/{id}/{uuid}` so the **same** `assertMediaPresignAccess` key-parse + gate apply unchanged (the gate keys off the announcement id in the path, not the table).
- **Endpoint surface + API-SPEC:** 3 new endpoints under §Announcements; reuses the contract-attachment naming already spec'd (`docs/API-SPEC.md:1608`). Detail response gains a parallel `attachments[]` manifest (filename + size + download URL).
- **Presign/gate reuse:** full reuse — same `FileStorageService.presign(+disposition)` + same gate. **Caps/types fully independent** (cleaner — no kind branching in the image path).
- **FE model:** attachments are their own list (filename + size + download), never a body-placeholder; the image media manager (`AnnouncementMediaManager.tsx`) is untouched.
- **Effort:** medium (new table + entity + repo + 3 endpoints + DTO + manifest field). **Risk:** code duplication of the upload/caps/cleanup scaffolding vs the cleaner separation of concerns.

**Tradeoff one-liner:** (A) least code, but overloads a placeholder-semantics table and forces kind-branching through the shared image caps/validation; (B) more scaffolding, but documents get a clean independent model with zero risk to the image path.

---

## STEP 4 — Allowed types + caps (options)

### 4.1 Document type allow-list (Tika magic-byte, extend `detectMime`)
Propose: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (docx), `…spreadsheetml.sheet` (xlsx), `…presentationml.presentation` (pptx), `text/plain` (txt), `text/csv` (csv). Detected by Tika on bytes, same mechanism as images.
- **EXPLICITLY exclude** renderable/script-capable types: `text/html`, `image/svg+xml`, and any browser-executable type — **unless** served as forced-download (§2.5). Macro/JS risk in Office docs is mitigated by **download-not-execute** (the browser never runs them; the user opens them in a local app at their own discretion — standard email-attachment posture).
- `[TODO: kiểm tra]` whether `.csv`/`.txt` should be allowed at all (CSV-injection into spreadsheet apps is a downstream concern; forced-download + nosniff covers the browser side).

### 4.2 Caps (options)
Current servlet cap is **10 MB/file** (`application.yml:27`) with `max-request-size: 55MB` (`:28`). Documents are typically larger than images.
- **Per-file:** option to raise the document per-file cap (e.g. 20 MB, matching the spec'd contract-attachment ceiling `API-SPEC:1615`). **Note:** the servlet `max-file-size`/`max-request-size` is **global** (applies to image uploads too) — raising it loosens the image path unless enforced per-kind in-service. Recommend a **service-level per-file byte cap for attachments** (independent of the servlet cap) rather than only bumping the global servlet limit.
- **Count + total:** propose a separate `MAX_ATTACHMENTS_PER_ANNOUNCEMENT` (e.g. 5–10) and `MAX_ATTACHMENT_TOTAL_BYTES` (e.g. 50–100 MB), **independent** of the image caps (`:92`/`:95`) — see OPEN RULING.

---

## STEP 5 — FE surfaces (enumerate, no code)

### 5.1 Admin authoring (draft-only)
An **attachments section** on `/:id/edit` parallel to the image media manager. Slots beside the existing manager mount in `AnnouncementEditPage.tsx:162` (`<AnnouncementMediaManager … />`) — a sibling `<AnnouncementAttachmentsManager>` block (upload + list of filename + size + delete). Like images, it is **draft-only** (the BE upload/delete gate is draft-only, `requireDraftForMedia` `:730`). Mirrors the existing manager's error mapping incl. the 413 special-case (`AnnouncementMediaManager.tsx:67-70`). **No body-placeholder/insert** — attachments are a list, not inline content.

### 5.2 Resident detail (download list)
A downloadable **attachments list** (filename + size + a download link to the presigned, scope-gated URL) on the resident detail page `frontend/apps/resident/src/pages/AnnouncementDetailPage.tsx`. It sits alongside the existing `MarkdownContent` body (`:55`) and cover banner (`:40-47`) — **no renderer change** (attachments don't flow through `MarkdownContent`; they're rendered as a plain link list). Access is the same `buildMediaManifest`/`assertMediaPresignAccess` gate (out-of-scope resident → empty list, no leak). The download URL must carry the §2.2 `Content-Disposition: attachment` so the browser downloads rather than renders.

---

## OPEN RULINGS FOR CTO

1. **Architecture:** EXTEND `announcement_media` with an `ATTACHMENT` kind **(A)** vs a NEW `announcement_attachment` table + parallel endpoints **(B)**. *(Investigator lean: (B) — cleaner separation, zero risk to the image caps/validation path, reuses the spec'd contract-attachment naming; (A) is less code but overloads placeholder semantics.)*
2. **Serving posture (RECOMMEND — confirm):** force `Content-Disposition: attachment` + `response-content-type=application/octet-stream` (signed via SDK `extraQueryParams`), add `X-Content-Type-Options: nosniff` on the :8090 nginx front, and **block renderable/script-capable types** (html, svg, anything browser-executable) at upload.
3. **Allowed types + caps:** confirm the type set (pdf/docx/xlsx/pptx/txt/csv — or a subset) and the per-file / per-announcement count / total-bytes caps.
4. **Shared vs own caps:** do attachments **share** the image caps (5 files / 50 MB total) or get **their own** independent caps? *(Lean: own caps — documents are larger and serve a different purpose.)*
5. **Prod delivery interaction:** the §2.4 cross-origin/cookie-scope and the forced-download posture depend on the **[PLANNED]** prod MinIO-front subdomain (DECISIONS 2026-06-25). `[TODO]` confirm the prod object host + cookie scope before prod ship.

---

## Proposed phase breakdown (PENDING ruling — do not start)
- **P1 (BE):** migration (CHECK alter for (A) **or** new table for (B), nullable cols + FK CASCADE per precedent, no drops) + endpoints + `presign()` overload for `Content-Disposition: attachment` + Tika document allow-list + reuse `assertMediaPresignAccess` gate + caps + tests (Tika reject of html/svg, cap enforcement, forced-download URL params, scope gate). API-SPEC §Announcements updated same phase.
- **P2 (FE admin):** attachments manager on `/:id/edit` (upload/list/delete, draft-only).
- **P3 (FE resident):** download list on the detail page (filename + size + scope-gated download).

All `[TODO]` markers above need CTO/prod input; nothing invented.
