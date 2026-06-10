# Diagnosis — Cluster 2 Announcements (#6 Create, #7 Publish)

## 1. Toaster / mutation hooks

| File | Location | Current state |
|------|----------|---------------|
| Admin AnnouncementsPage | `frontend/apps/admin/src/pages/AnnouncementsPage.tsx` | Component + inline error state |
| `useCreateAnnouncement` | `frontend/apps/admin/src/api/hooks.ts:118` | `meta: { skipErrorToast: true }` — no successMessage |
| `usePublishAnnouncement` | `frontend/apps/admin/src/api/hooks.ts:127` | `meta: { skipErrorToast: true, skipSuccessToast: true }` — no successMessage |

### Current error/success handling

| Form | Error | Success |
|------|-------|---------|
| #6 Create | `err?.response?.data?.message ?? 'Tạo thông báo thất bại'` — raw `.message` (English/leaked server text) | Silent: `setShowCreate(false)` only, no toast |
| #7 Publish (table row) | Not displayed — `skipErrorToast:true`, no inline state | Not displayed — `skipSuccessToast:true`, no toast |
| Publish confirm | `window.confirm('Publish this announcement?')` — English browser dialog | n/a |

## 2. BE ErrorCodes per operation

### POST /api/announcements (create) — `AnnouncementServiceImpl.java`

| Code | Source | Line |
|------|--------|------|
| `VALIDATION_ERROR` | @Valid bean validation on `CreateAnnouncementRequest` | controller:114 |
| `VALIDATION_ERROR` | scope constraint: BLOCK scope + no blockId | ServiceImpl:377 |
| `VALIDATION_ERROR` | scope constraint: FLOOR scope + no blockId/floor | ServiceImpl:381 |
| `NOT_FOUND` | user not found by principalId | ServiceImpl:154 |
| `NOT_FOUND` | block not found by targetBlockId | ServiceImpl:170 |

### POST /api/announcements/{id}/publish (publish) — `AnnouncementServiceImpl.java`

| Code | Source | Line |
|------|--------|------|
| `NOT_FOUND` | announcement not found | ServiceImpl:360 |

No ALREADY_PUBLISHED code exists. See section 3.

## 3. Publish safety — double-publish guard

**Finding: BE does NOT reject double-publish. Publish is intentionally idempotent.**

`AnnouncementServiceImpl.publishAnnouncement()` lines 264–268:
```java
// Idempotent: already published announcements are returned without error.
if (announcement.getPublishedAt() != null) {
    log.debug("publishAnnouncement — id={} already published, returning as-is.", id);
    return toResponse(announcement);
}
```
Second publish call returns HTTP 200 with existing data. No exception thrown.

**Notifications are STUBBED — not yet wired:**
- `AnnouncementServiceImpl` injects: `AnnouncementRepository`, `AnnouncementReadRepository`, `BlockRepository`, `UserRepository`, `ResidentRepository` — **no `NotificationService`**.
- Publish only logs: `log.info("Announcement {} published by {}. Delivery channels — push={}, email={}, sms={}", ...)`.
- Code comment: *"Stub: log delivery intent. Full push/email/SMS dispatch wired in Module 10."*
- Confirmed via `grep announcement NotificationServiceImpl.java` → **no matches**.
- No Spring `@EventListener` for announcement publish found anywhere.

**BLOCKER assessment:**
The BLOCKER criterion is "clicking twice duplicates notifications for ~1000 residents." This exploitation path does NOT exist: no notification rows are created at publish time. Notification dispatch is a stub. No BLOCKER required.

CTO context note: "sends REAL in-app notifications to all residents (~1000)" — **this is not yet true in the current codebase**. When Module 10 notification dispatch is wired to publish, the idempotent behavior should be re-evaluated and `ALREADY_PUBLISHED` should be added. For now: no action required.

**UI defense-in-depth already present:** table row `!a.publishedAt` check hides the Publish button once published (line 86).

## 4. getVnErrorMessage coverage

All announcement-relevant BE codes are already mapped:

| Code | Mapped | VN message |
|------|--------|------------|
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `CONFLICT` | ✅ | "Thao tác không thể thực hiện do xung đột dữ liệu." |
| `INVALID_STATUS_TRANSITION` | ✅ | "Không thể chuyển trạng thái trong bước này." |

**No missing codes → no feat(ui) commit needed.**

## Fix plan

1. `hooks.ts`: add `successMessage: 'Đã tạo thông báo.'` to `useCreateAnnouncement`; replace `skipSuccessToast: true` with `successMessage: 'Đã đăng thông báo tới cư dân.'` in `usePublishAnnouncement`.
2. `AnnouncementsPage.tsx`:
   - Import `getVnErrorMessage` from `@gemek/ui`.
   - Create error: `setFormError(getVnErrorMessage(err?.response?.data?.error))`.
   - Publish-from-create error: keep context message, append `getVnErrorMessage(code)`.
   - Replace `window.confirm` with VN modal confirm dialog (`showPublishConfirm` + `pendingPublishId` states).
   - Add `publishError` state; show above table; clear on new publish attempt.
   - Publish button guard: already `!a.publishedAt` ✓ — retain.
