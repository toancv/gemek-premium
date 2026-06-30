# Contractor Documents — FE Phase P3a

**Branch:** `feature/contractor-contract-upload`
**Date:** 2026-06-28
**Scope:** Admin app only. `ContractorDocumentsManager` on the contractor EDIT page (id always present).
NO lazy-save, NO `/new` mounting (that is P3b). NO BE / auth / schema / API-SPEC change (P1 endpoints already
exist + spec'd). Smoke gate: build-green + `/code-review`, then STOP for CTO :80 smoke (which also discharges
the deferred P1 BE live-verification).

## Ground truth (verified)
- HEAD `4e19ce7` on `feature/contractor-contract-upload`. P1 commits present (`093465b` feat /`6d8c611` test /
  `62e5258` docs) AND P2 (`5d2df1a` feat / `4e19ce7` docs). PROGRESS top snapshot authoritative (P1 BE
  done-unsmoked, P2 FE pages done). Git matches — no STOP condition.
- P1 endpoints confirmed against the controller (`ContractorController.java`):
  - POST `/api/contractors/{id}/documents` (multipart `file`), `@PreAuthorize hasRole('ADMIN')` — line 323.
  - GET `/api/contractors/{id}/documents`, `hasAnyRole('ADMIN','BOARD_MEMBER')` — line 341.
  - DELETE `/api/contractors/{id}/documents/{documentId}`, `hasRole('ADMIN')` — line 361.
  - Match `reports/contractor-documents-p1.md` exactly. No BE change required.
- `ContractorDocumentResponse` shape (dto): id, displayFilename, contentType, sizeBytes, createdAt,
  nullable downloadUrl — `dto/ContractorDocumentResponse.java`.

## Template studied (C3) — reproduced faithfully
- `frontend/apps/admin/src/components/AnnouncementAttachmentsManager.tsx` — upload/list/delete/download UX,
  inline size/count pre-checks (`onPick` :91), 413 error mapping (`errorText` :86), caps display, scheme-guarded
  forced-download anchor (`safeHref` :81), Escape-to-dismiss delete dialog (:71).
- Admin react-query patterns: `hooks.ts` `get/post/put/del` (:5-9), announcement attachment hooks (:290-315),
  MutationCache `meta.successMessage` top-right toast + `meta.skipErrorToast`. `getVnErrorMessage` from
  `@gemek/ui` (`packages/ui/src/lib/errorMessages.ts`).

## Key divergence from C3 (by design — list is a separate endpoint)
Announcement attachments are embedded in the draft detail manifest (parent passes `attachments[]` as a prop);
contractor documents are a SEPARATE GET endpoint. So the manager OWNS its list query
(`useContractorDocuments`, key `['contractors', id, 'documents']`) and self-fetches on mount; mutations
invalidate that exact key with `refetchType:'all'` (mirrors the announcement managers). No prop-drilling.

## What was built (files)
**New:**
- `components/ContractorDocumentsManager.tsx` — mounted on `ContractorEditPage` only (id present), as a
  sibling of the `<form>` (documents save immediately via their own endpoints, not on form submit).
  List rows: displayFilename + created date + size + forced-download anchor + delete. Loading / error /
  empty states. Delete-confirm dialog (VN). Boundary-correct local `formatSize` + `formatDate`.

**Modified:**
- `api/hooks.ts` — `useContractorDocuments` / `useUploadContractorDocument` / `useDeleteContractorDocument`
  (after `useUpdateContractor`).
- `pages/ContractorEditPage.tsx` — import + mount the manager; removed the P3 placeholder comment.
- `packages/ui/src/lib/errorMessages.ts` — 3 new keys: `CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED` (400),
  `CONTRACTOR_DOCUMENT_TOO_LARGE` (413), `CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED` (400). Additive; shared-ui
  infra (not the resident app). Parallels the existing `ANNOUNCEMENT_ATTACHMENT_*` keys.
- `packages/ui/src/lib/errorMessages.test.ts` — +1 assertion block + the 3 codes in the known-codes sweep.

## 413 size-branch (sanctioned divergence, DECISIONS 2026-06-28)
`errorText` keys off `status === 413` → ONE VN message "Tệp quá lớn (tối đa 10MB mỗi tệp)", covering BOTH
`CONTRACTOR_DOCUMENT_TOO_LARGE` (413, service cap) AND `PAYLOAD_TOO_LARGE` (413, servlet limit) regardless of
body — did NOT copy C3's 400-size branch. The 400 codes (`TYPE_NOT_ALLOWED`, `LIMIT_EXCEEDED`) route through
`getVnErrorMessage`. Inline pre-checks (≤10MB/file, ≤5 files, ≤50MB total) fire before any upload.

## formatSize — boundary-correct (P3a requirement)
New local `formatSize` rounds KB first and promotes to MB if rounding hits 1024, so KB never displays ≥1024.
The known-buggy admin announcement `formatSize` (`(bytes/1024).toFixed(0)` → "1024 KB" just under 1MB) is
**LEFT OPEN** — not touched this phase (announcement module out of scope). Logged as still-open backlog.

## Staff surface
Edit page is ADMIN-only → full upload/delete surfaced for ADMIN. BOARD_MEMBER read-access stays API-only this
phase (no FE surface) — deferred FE surface, noted in PROGRESS.

## Verification
- `npx vitest run packages/ui/.../errorMessages.test.ts` → 34 passed.
- admin `npx tsc --noEmit` → exit 0.
- admin `npx vite build` → 769 modules, built in 4.73s, SUCCESS.
- No admin vitest harness (backlog gap) → verified by build-green + the pending CTO :80 smoke.

## `/code-review` (high) — workflow-backed (23 agents)
8 finder angles → candidates → independent verify → 7 verified findings + 6 refuted. **0 correctness bugs**
(all UX/affordance or code-quality). Triage (CTO rule: apply trivial/sibling-aligning, defer scope-widening):

| # | File:line | Verdict | Finding | Disposition |
|---|---|---|---|---|
| 1 | Manager:129/159 | CONFIRMED | Total-size pre-check sums the loaded list; on `isError` the list falls back to `[]` so the 50MB-total guard is silently disabled and a file the FE OK's is then BE-rejected. | **Fixed.** Upload button now also disabled on `isLoading`/`isError` — never admit an upload against an unknown list. (The `sizeBytes==null` undercount is C3-inherited and kept.) |
| 5 | Manager:50 | CONFIRMED | Local `formatDate` re-implements shared `@gemek/ui formatVNDate` (NEW code, not a C3 pattern); `toLocaleDateString('vi-VN')` renders unpadded/engine-variant dates inconsistent with the rest of admin. | **Fixed.** Deleted local helper; use `formatVNDate` (zero-padded dd/MM/yyyy). |
| 6 | Manager:158 | CONFIRMED | Date span double-guarded (`d.createdAt &&` + `formatDate`'s internal guard). | **Fixed.** Single guard on `formatVNDate(d.createdAt)`. |
| 4 | hooks:228 | CONFIRMED | Upload/delete mutations set no `successMessage` → global generic toast (identical to C3). | **Fixed (improve).** Added doc-specific `successMessage` ('Đã tải lên tài liệu.' / 'Đã xoá tài liệu.'), aligning with the dominant admin mutation convention. |
| 2 | Manager:122 | CONFIRMED | Shared `busy` cross-disables upload/delete; label only flips on `upload.isPending`. | **Defer.** Verbatim C3 `AnnouncementAttachmentsManager` pattern (faithful reproduction); transient UX, no data issue. Logged, not drift. |
| 3 | hooks:208 | PLAUSIBLE | `useUpdateContractor` invalidates broad `['contractors']`, incidentally refetching the documents query on contractor save. | **Defer.** Pre-existing hook behavior (NOT new P3a code); refetch returns correct data. Scoping it would change unrelated contractor-save behavior. Logged. |

Refuted (6, no action): all DRY/extract-a-shared-primitive notes (formatSize/safeHref/413-branch/whole-component
clone) — faithful C3 reproduction, consistent with the pre-approved P1 cross-module DRY deferral.

Net: 4 trivial/sibling-aligning fixes applied; 2 deferred (C3-faithful + pre-existing hook). Re-build GREEN
(admin `tsc --noEmit` exit 0; `vite build` 769 modules SUCCESS; ui errorMessages test 34 pass).
