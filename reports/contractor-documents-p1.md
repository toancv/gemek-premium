# Contractor Documents — BE Phase P1

**Branch:** `feature/contractor-contract-upload`
**Date:** 2026-06-28
**Scope:** Backend only. Staff-only contract-document upload/list/delete on CONTRACTORS, mirroring the C3 announcement-attachment stack. No frontend. No ticket changes. No unrelated debt touched.

## CTO ruling implemented (DECISIONS 2026-06-28)
Contract documents attach to the **CONTRACTOR** entity (option C) as a row-per-file list in a new table
`contractor_document`, reusing the C3 forced-download attachment stack — NOT the existing `Contract`
entity. This **supersedes** the spec'd-but-unbuilt `POST/GET /api/contracts/{id}/attachment` and the
dormant `contracts.attachment_url` column (both kept write-idle, NOT dropped). Access is **staff-only**:
ADMIN uploads/deletes, ADMIN+BOARD_MEMBER read/download, TECHNICIAN and RESIDENT excluded (§13 R-4) —
deliberately omitting the announcement gate's resident-readable branch.

## STOP-condition pre-checks (all clear)
- Branch `feature/contractor-contract-upload` present; investigation report committed (`f30b18e`). ✔
- HEAD reconciled to PROGRESS snapshot (trunk=main, phone-search+C3 CLOSED). ✔
- **413 handler is GLOBAL, not announcement-coupled** — `GlobalExceptionHandler.handleMaxUploadSize`
  is `@RestControllerAdvice` (GlobalExceptionHandler.java:197-213) with a path-inferred code and a
  generic `PAYLOAD_TOO_LARGE` fallback; reused as-is. No STOP. ✔
- No migration drops/alters `contracts` (V23 only CREATEs `contractor_document`; DB-SCHEMA adds a
  SUPERSEDED comment beside `contracts.attachment_url`, no DDL change). ✔
- Flyway clean guard still touches only `gemek_test` (`AbstractIntegrationTest.java:98`, endsWith
  `/gemek_test`); V23 auto-migrated (suite log: "Successfully validated 23 migrations"). ✔
- DB/HTTP verification is possible (local Postgres :5433, real-DB MockMvc). ✔

## What was built (files)
**New:**
- `db/migration/V23__create_contractor_document.sql` — table + FK CASCADE (contractor) / SET NULL (creator) + index.
- `entity/ContractorDocument.java` (extends `CreatableEntity`, append-only, `@PrePersist` createdAt).
- `repository/ContractorDocumentRepository.java` (count / sumSizeBytes(COALESCE) / findOrdered / dual-key find).
- `dto/ContractorDocumentResponse.java` (id, displayFilename, contentType, sizeBytes, createdAt, nullable downloadUrl).
- Tests: `ContractorDocumentServiceIntegrationTest` (11), `ContractorDocumentControllerTest` (9).

**Modified:**
- `ErrorCode.java` — `CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED` (400), `CONTRACTOR_DOCUMENT_TOO_LARGE` (413),
  `CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED` (400).
- `ContractorService` + `ContractorServiceImpl` — `uploadDocument` / `listDocuments` / `deleteDocument` /
  `assertContractorDocumentPresignAccess`; injected `ContractorDocumentRepository`, generic
  `FileStorageService`, `ApplicationEventPublisher`; constants + helpers (`detectDocumentMime` +
  `classifyZipContainer`, `documentExtensionFor`, `sanitizeDisplayFilename`, `parseContractorIdFromKey`,
  `scheduleObjectCleanup`, `toDocumentResponse`).
- `ContractorController` — 3 endpoints with `@PreAuthorize`.
- `docs/DB-SCHEMA.sql`, `docs/API-SPEC.md` (§8 + §13).
- `ContractorServiceImplTest` — constructor updated for the 3 new mocked deps (no behavior change).

## Reuse vs re-author (locked rule honored: did NOT extend announcement_attachment)
- **Reused as-is (generic):** `FileStorageService.upload` + forced-download
  `presign(key, contentDisposition, contentType)`, `ContentDispositionUtil.attachment`, `MinioConfig`
  dual client, `ObjectKeysObsoleteEvent` + `ObsoleteObjectCleanupListener` (after-commit cleanup),
  the global 413 handler.
- **Re-authored for contractors:** table/entity/repository, key convention
  `contractors/{contractorId}/documents/{uuid}`, the staff-only gate `assertContractorDocumentPresignAccess`
  + `parseContractorIdFromKey`, per-contractor caps, Tika allowlist constants.

## Endpoints + gate
| Method | Path | @PreAuthorize |
|---|---|---|
| POST | `/api/contractors/{id}/documents` (multipart `file`) | `hasRole('ADMIN')` |
| GET | `/api/contractors/{id}/documents` (rows + forced-download `downloadUrl`) | `hasAnyRole('ADMIN','BOARD_MEMBER')` |
| DELETE | `/api/contractors/{id}/documents/{documentId}` | `hasRole('ADMIN')` |

Gate `assertContractorDocumentPresignAccess(objectKey, principalId, role)`: parse contractorId from key
→ null ⇒ 403; ADMIN/BOARD_MEMBER allowed; all others (incl. TECHNICIAN, RESIDENT) denied. Never trusts a
client-supplied key. Contractor DETAIL response (`GET /api/contractors/{id}`) left UNCHANGED.

## Deliberate divergences from C3 (logged)
1. **`CONTRACTOR_DOCUMENT_TOO_LARGE` → HTTP 413** (C3's `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` is 400). Reason:
   the service-layer per-file cap and the servlet multipart limit then present the FE the same coded 413
   size signal; also makes the MockMvc oversize test deterministic (MockMvc bypasses the servlet limit, so
   the service cap is what fires).
2. **No draft gate** — contractors have no draft/published lifecycle, so upload is allowed for any existing
   contractor (404 if missing). Simpler than the C3 draft-only rule.

## Deferred debt (NOT fixed this phase — scope discipline)
- Cross-module DRY: the Tika MIME allowlist + caps constants duplicate the announcement set
  (`ALLOWED_DOCUMENT_MIME_TYPES`, `MAX_DOCUMENT_*`). Extraction to a shared constant/helper is deferred,
  consistent with prior C3 DRY-debt handling. Logged, not extracted.

## Verification (DB/HTTP, real-DB MockMvc → real PostgreSQL)
- New tests GREEN: `ContractorDocumentServiceIntegrationTest` 11, `ContractorDocumentControllerTest` 9,
  `ContractorServiceImplTest` 9 (constructor update).
- **Full suite: 457 → 477 (all pass, 0 fail, 0 error, 0 skipped). BUILD SUCCESS.** (+20 new tests.)
- Flyway: "Successfully validated 23 migrations"; schema at V23.
- Coverage of the task test matrix:
  - upload PDF ADMIN → 201, row persisted; list returns it with an https `downloadUrl` carrying
    `response-content-disposition=attachment` (controller test) + ArgumentCaptor proves
    `attachment;…filename` + `application/octet-stream` (service test).
  - list gate: ADMIN 200, BOARD_MEMBER 200, TECHNICIAN 403, RESIDENT 403.
  - upload/delete gate: ADMIN ok; BOARD_MEMBER 403; TECHNICIAN/RESIDENT 403.
  - oversize >10MB → 413 `CONTRACTOR_DOCUMENT_TOO_LARGE`, no row. HTML(renamed .pdf)/SVG/plain-zip →
    400 `CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED`, no row.
  - 6th file → `CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED`; total >50MB → `CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED`.
  - malformed object key in the gate → 403 (not 500); cross-contractor delete → 404 (dual-key).
  - OOXML docx/xlsx/pptx accepted (zip-peek); delete ADMIN 204 + after-commit MinIO cleanup verified.

## /code-review (high) — workflow-backed
8 finder angles → 24 candidates → 24 verified → 11 kept / 13 refuted → 7 reported. **0 confirmed
correctness bugs.** Triage (CTO rule: apply trivial/correctness, defer scope-widening debt, reject
anything contradicting the ruling):

| # | Verdict | Finding | Disposition |
|---|---|---|---|
| [0] | PLAUSIBLE | `listDocuments` gates on `rows.get(0)` key and swallows denial → empty list; a malformed first-row key could hide all docs from an authorized admin. | **Keep (note).** Inherited verbatim from C3 `buildAttachmentManifest`. Keys are server-generated (`contractors/{id}/documents/{uuid}`), so a malformed row cannot arise from this feature (no import path) — not triggerable in P1. Known C3-inherited limitation. |
| [1] | PLAUSIBLE | TOCTOU on the count/total caps under concurrent uploads (read-check-then-save, no lock). | **Defer (note).** Identical to the C3 announcement caps (faithful mirror). Hardening (DB constraint / SELECT…FOR UPDATE) would diverge from C3 and widen scope; revisit cross-cuttingly with C3, not here. |
| [3] | CONFIRMED | `assertContractorDocumentPresignAccess` role check is dead defense-in-depth (the `@PreAuthorize` set == the gate's allowed set); `principalId` param unused. | **Reject removal.** The gate is an EXPLICIT P1 deliverable and §13 R-4 is NORMATIVE ("MUST route through an assertPresignAccess-style ownership check before issuing URLs"). It also performs the malformed-key→403 guard (never pass a bad key to MinIO presign) and is intentional belt-and-suspenders. `principalId` kept for signature parity with `assertMediaPresignAccess` + future per-owner checks. Removing it would contradict the ruling. |
| [4] | CONFIRMED | Role-extraction pipeline duplicated from `AnnouncementController.extractRole`. | **Defer (note).** A shared `UserPrincipal.getRole()` / util touches the announcement + shared layers (scope-widening). The reviewer's own notes show this idiom is already copy-pasted across 8+ controllers — a pre-existing repo pattern, not introduced here. Logged as cross-cutting debt. |
| [5] | PLAUSIBLE | `classifyZipContainer` / `detectDocumentMime` byte-detection logic duplicated from `AnnouncementServiceImpl`. | **Defer (note).** Same family as the pre-approved deferred Tika/caps-constant DRY; the natural home is `common.storage`. Extracting now widens scope into the announcement module. Logged. |
| [6] | CONFIRMED | Javadoc cites `reports/contractor-documents-p1.md` which (at review time) only existed as `.tmp`. | **Resolved.** This phase's docs commit renames `.tmp` → `reports/contractor-documents-p1.md`, so the citation is valid. (Verifier corrected the line to `ContractorServiceImpl.java:93`.) |
| [7] | PLAUSIBLE | OOXML upload re-reads the multipart stream up to 3× (detect / classify / upload). | **Defer (note).** Faithful C3 parity; a micro-optimization (buffer-once) with no behavior change. Logged. |

Net: no code changes applied — every reported item is intentional-and-mandated ([3]), an inherited-from-C3
known limitation ([0],[1],[7]), deferred DRY debt consistent with the pre-approved deferral ([4],[5]), or
self-resolved by the report rename ([6]). Nothing contradicting the CTO ruling (no resident exposure, no
inline preview, no single-key attachment_url) was introduced.
