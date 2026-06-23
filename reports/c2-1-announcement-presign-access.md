# C2.1 — Announcement media presign: scope-mirroring access check

**Phase:** C2.1 (security gate landed BEFORE any announcement image upload exists — C2.2).
**Scope:** access control only. NO upload endpoint, NO media table, NO render changes, NO presign-expiry / feed-query / C1-markdown changes.
**Verdict:** stub replaced; full backend suite GREEN (397 tests, 0 failures); `AnnouncementRecipientConsistencyTest` untouched.

---

## The hole that was closed

`assertPresignAccess` routed the `announcements/` key prefix to a **stub that let ANY authenticated user presign** (in-code note: *"Intentionally no DB-row requirement yet"*). No announcement media exists yet, so nothing leaks today — but the moment C2.2 adds uploads, any logged-in resident could presign any (block/floor-targeted) announcement file regardless of scope. C2.1 closes this first.

## Key convention (DEFINED here, consumed by C2.2)

```
announcements/{announcementId}/{uuid-filename}
```

The **announcement id is the first path segment after the prefix**, so the access gate recovers it from the object key alone and mirrors that announcement's read scope. Single source of the prefix: `AnnouncementService.MEDIA_KEY_PREFIX = "announcements/"` (the presign dispatcher routes on the same constant; the old private duplicate in `TicketServiceImpl` was removed).

## Access rule (mirrors the feed scope; analogue of `enforcePhotoAccess`)

| Caller | Rule |
|---|---|
| ADMIN / BOARD_MEMBER | Allowed, unrestricted (drafts included — authoring preview). |
| RESIDENT | Allowed **iff** the announcement is PUBLISHED and its ALL/BLOCK/FLOOR scope matches one of the caller's ACTIVE residencies — the **same predicate** as the resident feed. A DRAFT's media is never resident-visible. |
| Any other role (e.g. TECHNICIAN) | Denied. |
| Malformed key / nonexistent announcement id | Denied (`403 FORBIDDEN`) — **never a 500**. |

This is enforced **independently** of the widened announcement-LIST rule, exactly as `enforcePhotoAccess` is independent of the public-ticket read bypass (per-context media check).

## Branch replaced (before → after)

- **File:** `backend/.../module/ticket/TicketServiceImpl.java`, `assertPresignAccess` — the `announcements/` branch (was ~`:808-813`).
- **Before:** `if (startsWith(ANNOUNCEMENT_KEY_PREFIX)) { /* any authenticated user; no DB-row check */ return; }`
- **After:** `if (startsWith(AnnouncementService.MEDIA_KEY_PREFIX)) { announcementService.assertMediaPresignAccess(fileUrl, principalId, role); return; }`
- The scope logic lives in the **announcement module** (next to the feed predicate it must stay consistent with):
  - `AnnouncementService.assertMediaPresignAccess(objectKey, principalId, role)` — parses the id (malformed → deny), routes ADMIN/BOARD → allow, RESIDENT → scope query, else deny.
  - `AnnouncementRepository.existsReadableByResident(announcementId, userId)` — JPQL exists query: `publishedAt IS NOT NULL` + an `EXISTS` over the caller's active (`moveOutDate IS NULL`) residencies with the **ALL/BLOCK/FLOOR clause that textually mirrors `findPublishedForApartment` / `findRecipientUserIds`**. Draft, nonexistent id, and out-of-scope all yield `false` (deny) — null-safe, no nullable-param JPQL anchoring (the literal compares against the real `a.scope` attribute).
- `TicketServiceImpl` gains an `AnnouncementService` dependency (no cycle — `AnnouncementServiceImpl` does not depend on ticket). Its one unit-test constructor caller was updated with a mock.

## Test matrix (all GREEN)

Unit — `AnnouncementServiceImplTest` (mocked repo, no Docker; routing + parse):

| Case | Result |
|---|---|
| RESIDENT in scope (query=true) | **allow** + forwards parsed id |
| RESIDENT out of scope (query=false) | deny 403 |
| RESIDENT draft / nonexistent (query=false) | deny 403 |
| ADMIN | allow, no scope query |
| BOARD_MEMBER | allow, no scope query |
| TECHNICIAN | deny 403, no scope query |
| Malformed key (no id segment) | deny 403, no query |
| Malformed key (non-UUID id) | deny 403 |

Integration — `AnnouncementMediaPresignAccessTest` (real DB + JPQL through the real service bean; published/draft/scope semantics):

| Case | Result |
|---|---|
| RESIDENT in scope, published media | **allow** |
| RESIDENT out of scope (block B media, resident block A) | deny 403 |
| RESIDENT on DRAFT media | deny 403 |
| RESIDENT on nonexistent announcement id | deny 403 (no 500) |
| ADMIN on DRAFT media | allow |
| BOARD_MEMBER on published media | allow |

Routing — `PresignPrefixRoutingTest` updated to the new rule: ADMIN unrestricted on well-formed key; outsider RESIDENT + TECHNICIAN denied; legacy id-less `announcements/{uuid}.jpg` now malformed → denied even for ADMIN.

## No-go confirmed

No upload endpoint, no `announcement_media` table, no image rendering, no admin UI, no presign-expiry / feed-query / C1-markdown changes.
