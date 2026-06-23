# C2.2 — Announcement media upload (on the C2.1-secured presign)

**Phase:** C2.2 — ADMIN media upload for announcements. Built on C2.1 scope-mirroring presign.
**Scope:** upload + list + delete + draft-delete cascade + after-commit object cleanup. NO rendering, NO `<img>`, NO `announcement-media:` placeholder, NO admin upload UI (all C2.3).
**Verdict:** full backend suite GREEN — **412 tests, 0 failures**. C2.1 presign tests + `AnnouncementRecipientConsistencyTest` untouched/passing.

---

## Schema — `announcement_media` (migration V21)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `announcement_id` | UUID FK | `REFERENCES announcements(id) ON DELETE CASCADE` (mirrors `ticket_photos`→`tickets`) |
| `object_key` | TEXT NOT NULL | C2.1 convention `announcements/{announcementId}/{uuid}` |
| `content_type` | VARCHAR(100) | the **Tika-detected** type stored at upload |
| `size_bytes` | BIGINT | feeds the 50 MB total cap |
| `kind` | VARCHAR(20) NOT NULL | `CHECK (kind IN ('COVER','INLINE'))`, default `INLINE` |
| `original_filename` | VARCHAR(255) | display only |
| `created_by` | UUID | `ON DELETE SET NULL` (auditing via `CreatableEntity`) |
| `created_at` | TIMESTAMPTZ | `@PrePersist` |

Index `idx_announcement_media_announcement_id`. FK CASCADE chosen so the DB removes rows on draft delete; the service still collects keys first to schedule the MinIO cleanup (DB rows + objects stay consistent).

## Endpoints (ADMIN unless noted)

| Method | Path | Rule |
|---|---|---|
| POST | `/api/announcements/{id}/media` | multipart `file`+`kind`; draft-only; Tika+caps in-tx; 201 metadata (no presigned URL) |
| GET | `/api/announcements/{id}/media` | ADMIN/BOARD — list metadata, oldest first |
| DELETE | `/api/announcements/{id}/media/{mediaId}` | draft-only; row in-tx + object after-commit; dual-key |

Draft delete (`DELETE /api/announcements/{id}`) extended: collects media keys → deletes announcement (rows cascade) → schedules after-commit object cleanup.

## Tika validation (on bytes, not extension)

`TIKA.detect(file.getInputStream())` → must be in `{image/jpeg, image/png, image/webp}` else `400 ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED`. The **detected** type is what gets stored as `content_type` and what picks the key extension — the client `Content-Type` header and filename extension are never trusted. Text renamed `.jpg` → text/plain → rejected; GIF → image/gif → rejected.

## Caps (server-side, inside the transaction)

`MAX_MEDIA_PER_ANNOUNCEMENT = 5`, `MAX_MEDIA_TOTAL_BYTES = 50*1024*1024`. Computed from `countByAnnouncementId` + `sumSizeBytesByAnnouncementId` (COALESCE → null-safe). Cover-replace adjusts the baseline (subtracts the replaced cover's slot/bytes) BEFORE the check, so replacing a cover never trips the caps. `> cap` rejects with `400 ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED`; landing exactly at 5 / exactly 50 MB is accepted. Servlet multipart cap is 10 MB/file (`application.yml`).

## Cover replace (chosen: REPLACE, not reject)

`kind=cover` with an existing cover → old row deleted in-tx (`flush`) + old object key published to the after-commit cleanup event; new cover saved. Result: exactly one cover per announcement.

## After-commit object cleanup (NEW mechanism)

No after-commit side-effect mechanism existed (ticket-photo delete is in-tx). Introduced a generic, reusable one in `common/storage`:
- `ObjectKeysObsoleteEvent(List<String> objectKeys)` — published inside the service tx (cover-replace, delete media, draft delete).
- `ObsoleteObjectCleanupListener` — `@TransactionalEventListener(phase = AFTER_COMMIT)` → `FileStorageService.delete(key)` per key, best-effort (delete already logs+swallows; an orphaned object is harmless and never rolls back the business op). On rollback the listener never fires, so a row that survived keeps its object.

## Test matrix (all GREEN)

`AnnouncementMediaServiceIntegrationTest` (12, non-transactional → real commit exercises after-commit):
upload jpg/png/webp accepted + key matches convention; text-renamed-.jpg rejected (Tika); GIF rejected; upload-to-published rejected; invalid kind rejected; 6th image rejected + 5th accepted; >50 MB rejected + exactly-50 MB accepted; cover-replace removes old row + deletes old object after commit; delete media removes row + deletes object after commit; delete-media-on-published rejected; draft delete cascades media rows + deletes both objects after commit.

`AnnouncementControllerTest` (+3): ADMIN upload to draft → 201 (kind/contentType/objectKey asserted); RESIDENT → 403; upload to published → 409 `ANNOUNCEMENT_NOT_DRAFT`.

`AnnouncementServiceImplTest` (18, C2.1 presign unit tests) — constructor extended with the 3 new deps, all still green.

## No-go confirmed

No image rendering, no `<img>` in the Markdown renderer, no `announcement-media:` placeholder resolution, no admin upload UI. Presign expiry, feed query, C1 renderer untouched.
