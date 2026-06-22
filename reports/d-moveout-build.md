# Backlog (d) — Resident Move-Out ("Kết thúc cư trú") — BUILD (Option B)

**Status: BUILT. admin tsc + vite build green. Awaiting CTO :80 smoke.**
CTO ruling: Option B — date picker (default today, editable) + optional notes. FRONTEND ONLY; BE unchanged.

## Surface note (decision)
No dedicated admin Resident DETAIL page/route exists — `/residents` (`App.tsx:68`, ADMIN-gated) renders `ResidentsPage` (list + create modal) and is the **only** resident admin surface. The move-out action + moved-out state were added to that list page (per-row), the de-facto resident management surface. Not a new architecture; logged in DECISIONS.

## BE contract (re-confirmed)
- `POST /api/residents/{id}/move-out`, `@PreAuthorize("hasRole('ADMIN')")` (`ResidentController.java:172-173`).
- Body `MoveOutRequest { moveOutDate: LocalDate @NotNull, notes: String optional }` (`MoveOutRequest.java:23-30`).
- Effect (`ResidentServiceImpl.java:254-279`): sets moveOutDate, clears primaryContact flag, appends MOVED_OUT history. Does NOT deactivate account / block login. Re-trigger → `RESIDENT_ALREADY_MOVED_OUT` (`:261-263`).
- No undo endpoint (only POST/PUT/move-out) → not reversible from UI.
- `ResidentResponse.moveOutDate` exposed, null = active (`:49`). List returns `PageResponse<ResidentResponse>`, so each row carries it.

## What was built
**`api/hooks.ts`** — `useMoveOutResident()`:
- `post('/residents/${id}/move-out', { moveOutDate, ...(notes ? {notes} : {}) })`.
- `meta.successMessage: 'Đã kết thúc cư trú của cư dân.'` → top-right toast (auto-fired by MutationCache).
- `meta.skipErrorToast: true` → errors shown inline.
- `onSuccess` → `invalidateQueries(['residents'])` → list refetch.

**`pages/ResidentsPage.tsx`**:
- `ResidentItem.moveOutDate: string | null` added.
- New "Trạng thái cư trú" column (colSpans 6→7):
  - `moveOutDate` non-null → badge **«Đã chuyển đi · {dd/MM/yyyy}»** (`formatVNDate`), no button.
  - null → **"Kết thúc cư trú"** button → opens confirm dialog.
- Confirm dialog (real modal, mirrors create-modal/P1 ADMIN-confirm pattern):
  - **Date**: `VNDatePicker`, defaulted to today via `toISODateLocal(new Date())`, editable (dd/mm display, ISO value).
  - **Notes**: optional `<textarea>` → `MoveOutRequest.notes` (omitted when blank).
  - **Confirm copy**: "Kết thúc cư trú của {fullName} tại căn {unitNumber}? Cư dân sẽ được đánh dấu đã rời đi và gỡ vai trò liên hệ chính. Thao tác này **KHÔNG** khoá tài khoản đăng nhập và **KHÔNG thể hoàn tác** từ giao diện." — states the REAL effect + irreversibility.
  - Confirm → `mutateAsync({ id, moveOutDate, notes? })`; error → inline VN via `getVnErrorMessage` (incl. `RESIDENT_ALREADY_MOVED_OUT`); success → close + toast + refetch.

## ISO-date-no-time confirmation
`moveOutDate` originates from `toISODateLocal(new Date())` (default) and `VNDatePicker.onChange` (edits) — both yield pure `yyyy-mm-dd` strings (KIND C wire format, local Y-M-D parts, no UTC round-trip, `VNDatePicker.tsx:7-9,64-65`). Payload sends `moveOutDate` verbatim — **no datetime-local / time component**. Matches BE `LocalDate`. (This is the backlog-(b) mm/dd/yyyy trap, avoided.)

## errorMessages key
`RESIDENT_ALREADY_MOVED_OUT` already present in the shared map → 'Cư dân này đã rời khỏi căn hộ.' (`errorMessages.ts:42`). **No key added.**

## Verify
- `npx tsc --noEmit` → exit 0.
- `npx vite build` → 590 modules, built in 3.81s, exit 0.
- /code-review (medium): reviewed the diff (2 files, self-contained). No correctness findings — pure ISO date confirmed, button guarded by `moveOutDate` (matches BE re-trigger guard), error mapped, refetch+toast wired. `catch (err: any)` matches the file's existing `handleCreate` convention. Nothing to defer.

## Moved-out state display
Driven by `r.moveOutDate`: non-null → badge + dd/MM/yyyy, button hidden; null → active button. After a successful move-out the list invalidates → row flips to badge automatically. Re-opening an already-moved-out resident shows the badge and no active button.
