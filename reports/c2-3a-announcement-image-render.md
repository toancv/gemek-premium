# C2.3a — Announcement image RENDER (resident side, safe internal `<img>`)

**Phase:** C2.3a — re-open the `<img>` surface C1 closed, but ONLY for internal announcement media.
**Scope:** shared-renderer safe-img + manifest resolution; resident detail cover banner + inline render;
backend media manifest on the detail response (fresh presigned, C2.1-gated). **NO** admin upload/insert UI,
**NO** authoring-page move (C2.3b).
**Verdict:** full backend suite **416/416 green** (412 + 4 new); UI renderer tests 12/12; resident + admin
apps tsc-clean. C1 / C2.1 / C2.2 tests + `AnnouncementRecipientConsistencyTest` untouched & passing.

---

## XSS posture (the gate this phase re-opens)

C1 deliberately excluded `<img>`. C2.3a re-enables it under a single rule, enforced in the **one shared**
`MarkdownContent` config (`frontend/packages/ui/src/components/MarkdownContent.tsx`) — no divergent copy:

- **A live `<img>` is emitted ONLY when** the markdown image `src` is the internal placeholder
  `announcement-media:{id}` **AND** `{id}` resolves to a manifest entry. The rendered `src` is then the
  **manifest's presigned URL**, never the author's string.
- Any other `src` renders **nothing** (or the alt text), never a live img:
  - `![](https://evil.com/x.png)` → not a placeholder → no img (no external/SSRF/tracking fetch).
  - `![](javascript:alert(1))` / `data:` → neutralised by urlTransform → no img.
  - `![x](announcement-media:deleted-id)` → id not in manifest → no img.
- Raw HTML `<img src=x onerror=alert(1)>` stays **inert escaped text** (no rehype-raw) — no live element,
  no handler.
- Only safe attributes set: `src` (resolved presigned URL), `alt` (escaped author/`title` text),
  `loading="lazy"`. **No** author-controlled `onerror`/`onload`, **no** style injection.
- All C1 protections intact: **no rehype-raw**, **zero `dangerouslySetInnerHTML`**, scheme-filtered links
  (`http/https/mailto` only), remark-breaks. The internal `announcement-media:` scheme is passed through the
  **global `urlTransform`** (so the img component can resolve it) but is **stripped on links** by the `a`
  component's own `safeUrlTransform` — an `[x](announcement-media:id)` link is inert.

Wiring: `makeImgComponent(manifest)` is the only manifest-dependent piece; the static element map and
`urlTransform` are module-level. `MarkdownContent` takes an optional `mediaManifest` prop; with no/empty
manifest (admin C1 preview, any other consumer) every placeholder renders nothing — no regression.

## Placeholder → manifest resolution

- **Manifest shape** (chosen): the detail response `media: [{ id, kind, url }]` where `kind` ∈ `COVER|INLINE`
  and `url` is a fresh presigned GET URL. The renderer maps `announcement-media:{id}` → entry by `id`.
- **Cover vs inline:** the `COVER` entry is rendered as a **banner** above the title by the resident page
  (`AnnouncementDetailPage.tsx`), NOT inline. Inline entries resolve inside the Markdown body. The cover is
  not duplicated inline (seeded/authored content puts only inline placeholders in the body).
- No raw object keys or long-lived URLs in the body or the client — only short-lived presigned URLs in the
  manifest, minted per request.

## Backend (minimal for render)

- `AnnouncementResponse` gains `List<MediaRef> media` (`MediaRef{ id, kind, url }`).
- `getAnnouncement` builds the manifest via `buildMediaManifest(announcement, principalId, role)`:
  loads media rows, **reuses the C2.1 `assertMediaPresignAccess` gate** (checked ONCE — every row of one
  announcement shares the same scope decision), and on allow mints `fileStorageService.presign(objectKey)`
  per row. Denial (out-of-scope resident / wrong role) → **empty manifest**, never a 500.
- Manifest is **detail-only**: `toResponse(...)` defaults `media` to empty for list / create / update /
  publish (no per-page presign storms, no leak on list).
- **No** change to the feed LIST (still scoped, no manifest), **no** change to DISTINCT multi-residency
  scope (`publishedForResidenciesSpec` untouched), **no** loosening of C2.1 presign, **no** upload/delete
  change. Hibernate null-safe (rows list guarded; presign only on allow).

## Test matrix

**Renderer** (`MarkdownContent.test.tsx`, 12/12):
| Case | Expectation |
|---|---|
| `announcement-media:{validId}` in manifest | `<img>` with presigned src, escaped alt, `loading=lazy`, no on* |
| placeholder id NOT in manifest | no img |
| `![](https://evil.com/track.png)` (+manifest) | no live img to that URL |
| `![](javascript:alert(1))` | no img, no `javascript:` in DOM |
| raw `<img src=x onerror=…>` | inert escaped text, no img, no `[onerror]` element |
| `[x](announcement-media:id)` link | no live `announcement-media:` href |
| C1 cases (script/js/data link, bold/italic/list/link, `\n`→`<br>`) | unchanged, green |

**Backend** (`AnnouncementDetailMediaManifestTest`, 4/4, real DB, mocked presign):
| Case | Expectation |
|---|---|
| RESIDENT in scope, published + cover&inline | manifest size 2, each url presigned for its key, both kinds present |
| RESIDENT out of scope (block B) | manifest empty; `presign` never called |
| ADMIN on DRAFT | manifest present (authoring preview) |
| published, no media | manifest empty |

Untouched & green: C1 renderer tests, `AnnouncementMediaPresignAccessTest` (6), `AnnouncementMediaServiceIntegrationTest` (12), `AnnouncementControllerTest` (10), `AnnouncementServiceImplTest` (18), `AnnouncementRecipientConsistencyTest` (4). Module 54/54; full suite **416/416**.

## Accepted limitation

Presigned URLs are minted fresh per detail request (10-min expiry). A page left open >10 min may show a
broken image — **accepted, no refresh mechanism** (CTO ruling).

## /code-review

Focused review (zero `dangerouslySetInnerHTML`, img only for internal resolved media, no arbitrary src,
C1 protections intact, manifest C2.1-gated & detail-only): **PASS WITH NOTES — 0 Must-fix**. Confirmed no
`dangerouslySetInnerHTML`/`rehype-raw`; img gate correct; `announcement-media:` stripped on links;
single-row C2.1 gate sound; out-of-scope → empty manifest, no 500, no leak; manifest detail-only.
LOW note (cover banner `src` unvalidated — server-minted, not a live vuln) **APPLIED**: banner now asserts
an `http(s)` scheme before binding `src`. Optional Javadoc note left as-is (method already documents the
single-row-share-scope rationale).

## CTO smoke checklist (PENDING — C2.3a not yet smoked)

Manual verification the CTO must perform before C2.3a is closed. **Setup:** run the seed helper —
`ADMIN_PHONE=… ADMIN_PASSWORD=… scripts/smoke-c2-3a.sh` — which creates a fresh published draft with the
cover banner + inline image + the 5 storable attack/edge cases below, and prints the resident detail path
plus one in-scope and one out-of-scope resident login.

**Raw-HTML note:** the raw `<img src=x onerror=alert(1)>` case is **NOT browser-smokeable** — it is rejected
at WRITE time (`400 ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED`, `AnnouncementServiceImpl.java:883`), so raw HTML
never reaches the renderer via the API. That vector is closed at write time (stronger than render-time
inertness); the renderer's raw-HTML escaping is covered by the UI unit tests, not this browser smoke. The
browser smoke therefore covers the **5 storable** attack/edge cases below + positive render + scope/leak.

XSS attacks (each MUST stay inert — no live `<img>`, no script, no handler firing):
- [ ] Body `![x](https://evil.example/track.png)` → NO live img to that URL (no external/tracking fetch in Network tab).
- [ ] Body `![x](javascript:alert(1))` → no img, no alert, no `javascript:` in DOM.
- [ ] Body `![x](data:text/html;base64,PHNjcmlwdD4=)` → no img, no `data:text/html` in DOM.
- [ ] Body `![x](announcement-media:00000000-0000-0000-0000-000000000000)` (unknown id) → renders nothing.
- [ ] Body `[click](announcement-media:{inlineId})` (link, not image) → inert anchor (no working `announcement-media:` href).

Positive render (MUST work):
- [ ] Cover image shows as a BANNER above the title; it is NOT also duplicated inline.
- [ ] Inline `announcement-media:{inlineId}` placeholder renders the image inside the body at its position.
- [ ] Both image URLs are short-lived presigned MinIO URLs (internal), not raw object keys or external URLs.

Scope / leak (MUST hold):
- [ ] A resident OUT of the announcement scope opening the detail (if reachable) sees text but NO image URLs (empty manifest).
- [ ] Page left open >10 min may show a broken image (presigned expiry) — expected, accepted, no refresh.

## No-go confirmed

No admin upload widget, no image-insert UI, no authoring-page move (all C2.3b). To exercise rendering,
seed a draft (admin-visible) or a published in-scope announcement with cover/inline placeholders + media
rows directly.

## Commit groups (separate)

1. `feat(ui)` — shared renderer safe-img + manifest resolution + 7 renderer tests.
2. `feat(be)` — manifest on detail response (`AnnouncementResponse.MediaRef`, `buildMediaManifest`) + 4 tests.
3. `feat(resident)` — detail cover banner + inline render + `AnnouncementItem.media`.
4. `docs(api)` — API-SPEC detail manifest + DECISIONS (safe-img rule, placeholder convention, fresh-presign limitation).
5. `docs(context)` — PROGRESS + this report.

## Smoke-seed helper

`scripts/smoke-c2-3a.sh` — re-runnable helper that seeds ONE fresh fixture against the running dev
stack so the CTO can run the checklist above. It only drives real, in-contract endpoints (login,
create, C2.2 media upload, update, publish, detail); it does **not** touch the DB, MinIO, Flyway, or
`gemek_test` directly, and changes no production code/schema/API-SPEC.

**Run:**
```bash
ADMIN_PHONE=0901100001 ADMIN_PASSWORD='Demo@1234' ./scripts/smoke-c2-3a.sh
```

**Env (documented defaults; no secrets hardcoded):**
- `BASE_URL` (default `http://localhost`)
- `ADMIN_PHONE` (required — admin login phone; demo admin is `0901100001`)
- `ADMIN_PASSWORD` (required — read from env; demo seed password is `Demo@1234`)

What it does: admin login → pick block[0] as the single in-scope `targetScope:BLOCK` target → create a
fresh DRAFT (no id collision per run) → upload one COVER + one INLINE via `POST /announcements/{id}/media`
(genuine 1×1 PNG bytes; Tika detects `image/png` from the magic bytes, so no client `Content-Type` is
sent) → `PUT` the body with all 7 checklist cases (legit inline, external-url, `javascript:`, `data:`,
raw `<img onerror>`, unknown-id, link-form) substituting the real `{inlineId}` → publish → print the new
announcement id, the resident detail path, and best-effort one in-scope + one out-of-scope resident phone.
All seed text is **ASCII by design**.

### Self-verify (HTTP level, 2026-06-25) — BLOCKED by a dev-env gap, NOT a helper/prod defect

Stack is UP (`gemek-backend` healthy on :80). Helper verified correct through publish-body:
`POST /auth/login` → `accessToken` ✓; `GET /blocks` ✓; `POST /announcements` → `201` with a minted id ✓
(confirmed by manual reproduction). **Step 4 (media upload) fails with `500 "File upload failed."`** The
backend log gives the unambiguous root cause:

```
FileStorageService : MinIO upload failed for key announcements/<id>/<uuid>.png:
  The specified bucket does not exist
io.minio.errors.ErrorResponseException: The specified bucket does not exist
```

The configured bucket is `gemek` (`application.yml` → `MINIO_BUCKET:gemek`). `gemek-minio` is healthy,
but the bucket was never created in this dev instance — `docker-compose.yml` documents 9001 console as
"first-run bucket setup only", a manual step not performed here. **This blocks C2.2/C2.3 media uploads
generally, not just this helper.** The task forbids touching MinIO directly, so the bucket was NOT
created and the ADMIN-detail `media` manifest verification is **PENDING**: after the `gemek` bucket
exists, re-run the helper and `GET /api/announcements/{id}` should return `media` with 2 presigned
entries (COVER + INLINE). No success was fabricated.

> Secondary observation (out of scope, lower confidence): `POST`/`PUT` announcement with non-ASCII
> `content` (Vietnamese diacritics, `…`) returned `500 "Invalid UTF-8 middle byte"` (Jackson) in this
> environment — possibly a client-encoding artifact under Git-Bash curl rather than a backend defect.
> The seed body is ASCII, so the helper is unaffected. Flagged for a separate look, not fixed here.
