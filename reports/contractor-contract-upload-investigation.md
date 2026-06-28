# Investigation — Contractor Management Upgrade: Dedicated Page + Contract Document Upload

**Branch:** `feature/contractor-contract-upload` (off `main` @ `be6de3b`)
**Date:** 2026-06-28
**Type:** READ-ONLY investigation. No application code changed. Surfaces evidence + options + decision points for the CTO. No rulings made.
**Method:** Direct read-only survey (Grep/Read). NOTE: the ECC `architect` agent was the suggested executor, but its toolset (Read/Write/Glob — no Grep/Bash) cannot capture the raw-grep evidence this deliverable requires, so the survey was run from the main thread with full read-only tooling.

---

## 0. Ground Truth

- `git status`: working tree clean on tracked files; only untracked `reports/*` + `scripts/GenHash.java` (pre-existing).
- `git log -1`: `be6de3b docs(context): trunk-rename runbook + secret/config audit + DECISIONS`.
- HEAD = `be6de3b`, branch was `main`. Matches `PROGRESS.md` snapshot (2026-06-26): trunk = `main`; phone-search CLOSED; C3 CLOSED. **No contradiction.** HANDOFF.md resume pointer (stale "C2.3a, 2026-06-23") was ignored as instructed.
- New branch `feature/contractor-contract-upload` created off `main`. All work here.

### ⛔ HEADLINE FINDINGS (read before the decision points)

1. **A separate `Contract` entity + table already exists** (`Contract.java`, table `contracts`, migration `V6`). The feature name says "contractor contract upload," but the domain already models contracts as first-class records linked to a contractor (1→many). This is the B conflict-check; it is **non-trivial** and is STOP-flagged below.
2. **`contracts.attachment_url` ALREADY EXISTS** as a column + entity field + response field — documented as "MinIO object key for the signed contract document" (`Contract.java:99-101`, `V6:15`). **But it is dormant: no write path.** It is absent from both `CreateContractRequest` and `UpdateContractRequest`, so nothing ever sets it; it is only read back into `ContractResponse` (MapStruct auto-map). There is **no upload pipeline** for it.
3. **The contract-attachment endpoints are ALREADY SPEC'D but NOT IMPLEMENTED.** `API-SPEC.md` §8 defines `POST /api/contracts/{id}/attachment` and `GET /api/contracts/{id}/attachment` (lines 1608-1640), both flagged "⚠️ NOT IMPLEMENTED + security gate (R-4)", with a proposed key convention `contracts/<id>/attachment/…` and a staff-only access ruling in the §13 file-surface matrix (line 2515).
4. **There is NO admin FE for contracts at all.** The admin app has a contractor list+modal (`ContractorsPage.tsx`) and a read-only "expiring contracts" report tab (`ReportsPage.tsx`), but **no create/edit/detail UI for the `Contract` entity**, despite the BE contract endpoints existing. So "where the contract file UI lives" is greenfield on the FE.

These four facts mean the feature as phrased ("upload a contract document for a contractor") collides with an existing, partially-designed Contract model. The CTO must rule on attach-point before any build (decision point 1).

---

## A. Existing Contractor Entity + API

### A.1 Entity + table
- Entity: `backend/.../module/contractor/entity/Contractor.java:39-119` (`@Table(name = "contractors")`, extends `AuditableEntity`).
  Fields: `id` (UUID), `companyName` (NOT NULL), `contactPerson`, `phone` (len 20, **nullable**), `email`, `address` (TEXT), `specialty` (enum `contractor_specialty`, NOT NULL default OTHER), `taxCode`, `rating` (NUMERIC 3,2), `notes` (TEXT), `active` (`is_active` NOT NULL default true), `createdAt`, `updatedAt`.
- Migration: `V5__create_contractors_and_tickets.sql:5-23` — `CREATE TABLE contractors (...)` + indexes on specialty/is_active/rating.
- Canonical schema mirror: `docs/DB-SCHEMA.sql:388-408`.

### A.2 Contractor endpoints (`ContractorController.java`)
| Method | Path | `@PreAuthorize` | Line |
|---|---|---|---|
| GET | `/api/contractors` (list; `search`, `specialty`, `active`, `page`, `size`) | `hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER')` | `:78-92` |
| POST | `/api/contractors` (create) | `hasRole('ADMIN')` | `:101-109` |
| GET | `/api/contractors/{id}` (detail) | `hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER')` | `:117-122` |
| PUT | `/api/contractors/{id}` (update) | `hasRole('ADMIN')` | `:132-140` |
| DELETE | `/api/contractors/{id}` (soft-delete `is_active=false`) | `hasRole('ADMIN')` | `:148-154` |

- Create body `CreateContractorRequest` (`CreateContractorRequest.java:23-32`): `companyName`(@NotBlank), `contactPerson`, `phone`, `email`, `address`, `specialty`(@NotNull), `taxCode`, `notes`.
- Update body `UpdateContractorRequest` (`UpdateContractorRequest.java:23-32`): same fields, all optional.
- Response `ContractorResponse` (`ContractorResponse.java:29-42`): id, companyName, contactPerson, phone, email, address, specialty, taxCode, rating, notes, active, createdAt.
- Service anchor (phone-search touched it): `ContractorServiceImpl.java:107` — list filter ORs `companyName|contactPerson|phone` (Criteria API).

### A.3 Current admin FE for contractors — it is a MODAL
- `frontend/apps/admin/src/pages/ContractorsPage.tsx`. Add (`:42`) and Edit (`:73`) both open a single `modal` state (`:12`). The modal is rendered inline at `:88-124` (`fixed inset-0 ... bg-black/50`), a `<form onSubmit={handleSubmit}>` (`:93`).
- Form fields (`:94-113`): `companyName`* , `contactPerson`, `phone`, `email`, `specialty` (select), `address`, `taxCode`, `notes`. (No contract / no file field.)
- Submit (`:24-35`) calls `useCreateContractor` / `useUpdateContractor` (hooks `hooks.ts:189-205`). Errors → inline `formError`.
- Route: `App.tsx:74` — `<Route path="contractors" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ContractorsPage/></RequireRole>}>`. (Note route allows BOARD_MEMBER to reach the page; the page itself hides write controls behind `isAdmin` — `ContractorsPage.tsx:15-17,41,73`.)
- **Scope of "move to a dedicated page":** today everything is one modal with 8 text fields. A dedicated `/contractors/new` + `/contractors/:id/edit` page would replace the modal block (`:88-124`) and add routes; the field set is small.

---

## B. Existing "Contract" Entity — CONFLICT CHECK (CRITICAL)

**A separate Contract model exists and is fully built on the BE.** This is the crux of the feature.

### B.1 The Contract entity + table
- Entity: `Contract.java:50-140` (`@Table(name="contracts")`, extends `AuditableEntity`, `@AttributeOverride createdBy → created_by_user_id`).
  Fields: `id`, `contractor` (`@ManyToOne LAZY optional=false`, FK `contractor_id` — `:62-64`), `title` (NOT NULL), `scope` (TEXT), `contractValue` (NUMERIC 18,2), `currency` (default VND), `startDate` (NOT NULL), `endDate` (nullable = open-ended), `status` (enum `contract_status`: PENDING/ACTIVE/EXPIRED/TERMINATED — `ContractStatus.java:10-19`), **`attachmentUrl` (`attachment_url` VARCHAR(1000)) — `:99-101`**, `notes`, `expiryNotifiedAt`, `createdAt`, `updatedAt`.
- Migration: `V6__create_contracts.sql:5-27`. Relationship: `fk_contracts_contractor … REFERENCES contractors(id) ON DELETE RESTRICT` (`V6:21`) — a contractor with contracts cannot be hard-deleted (matches the soft-delete-only contractor design). DB-SCHEMA mirror `docs/DB-SCHEMA.sql:583-608` (`attachment_url … -- MinIO object key for contract PDF`, `:593`).
- Cardinality: **Contractor 1 → many Contract.** Also `contract_payments` (1 Contract → many payments, `V6:30-44`) and `maintenance_schedules` (1 Contract → many schedules, `V6:47-63`).

### B.2 The dormant `attachment_url` — exists but unwritable
- `attachmentUrl` is declared on the entity (`Contract.java:99-101`) and surfaced in `ContractResponse` (`ContractResponse.java:42`, doc `:26`).
- It is **NOT** in `CreateContractRequest` (`CreateContractRequest.java:26-35` — no attachment field) nor `UpdateContractRequest` (`UpdateContractRequest.java:24-31`).
- Grep for write usage in the contractor module: `attachmentUrl` appears ONLY in `Contract.java` + `ContractResponse.java` (no service/mapper setter):
  ```
  contractor/entity/Contract.java:101:    private String attachmentUrl;
  contractor/dto/ContractResponse.java:42:        String attachmentUrl,
  ```
  No `Minio|presign|MultipartFile` anywhere under `module/contractor` (grep returned 0 such hits).
- **Conclusion:** the field is a vestigial single-key placeholder. It is read back into the response (always `null` in practice) but no API or service path ever sets it. No upload, no presign, no forced-download for contracts today.

### B.3 Spec'd-but-unbuilt contract attachment endpoints
`API-SPEC.md` already anticipates this feature (§8, lines 1608-1640):
- `POST /api/contracts/{id}/attachment` — Auth ADMIN; `multipart/form-data` field `file` (**PDF only, max 20 MB** per spec); returns `{ "objectKey": "contracts/<id>/attachment/contract.pdf" }`. Flagged ⚠️ NOT IMPLEMENTED + R-4.
- `GET /api/contracts/{id}/attachment` — Auth ADMIN, BOARD_MEMBER; returns `{ presignedUrl, expiresAt }`; `404 NOT_FOUND` if none. Flagged ⚠️ NOT IMPLEMENTED + R-4.
- These are SINGLE-FILE-per-contract ("upload or replace the PDF attachment") and map naturally onto the dormant `contracts.attachment_url` column. They contradict the locked C3 "no single-key, use a row-per-file table" precedent (see C / decision points 2 & 7).

### B.4 Where the uploaded contract FILE could attach — three options (NOT decided)
- **(i) Directly on `Contractor`** — a contract doc as a property of the contractor record. Evidence-for: simplest for "a contractor's contract PDF"; matches the literal feature phrasing; no need to first create a Contract record. Evidence-against: ignores the existing Contract model; a contractor can have many contracts over time, so one field on Contractor under-models reality; no natural place for per-contract docs.
- **(ii) On the existing `Contract` entity** — populate/replace `contracts.attachment_url`, or (better, per C3 precedent) a child table keyed by `contract_id`. Evidence-for: the domain ALREADY models contracts; `attachment_url` + spec'd endpoints + §13 R-4 ruling were all designed for exactly this; FK + scheduler + payments already exist. Evidence-against: **there is currently NO admin FE for contracts** (B.5), so choosing this means also building contract CRUD UI (bigger scope than "a dedicated contractor page"); and it requires the user to create a Contract record before/while uploading.
- **(iii) A new standalone contract-document record/table** keyed to contractor or contract. Evidence-for: cleanest reuse of the C3 row-per-file precedent (decision point 7); supports multiple docs + audit columns; avoids reviving the single-key `attachment_url`. Evidence-against: introduces a parallel concept next to the existing Contract entity; the FK target still depends on (i) vs (ii).

> **STOP-FLAG:** the existing Contract model makes the attach-point a genuine design fork, not a detail. The feature request ("contractor … upload a contract document") is ambiguous between "a contractor-level PDF" and "a document on a Contract record." This must be a CTO ruling (decision point 1) before any code. The spec's existing `contracts/{id}/attachment` design points at option (ii); the locked C3 row-per-file precedent points at a child table under whichever owner is chosen.

### B.5 No contract management UI exists
Grep `contract|Contract` across `frontend/apps/admin/src`: the only contract surfaces are the **read-only** expiring-contracts report (`ReportsPage.tsx:161-186`, hook `useContractsExpiringReport` → `/reports/contracts-expiring`, `hooks.ts:402-403`) and dashboard counts (`DashboardPage.tsx:38,48`). There is **no** create/edit/detail page for `Contract`, and no hook hitting `/api/contractors/{id}/contracts` or `/api/contracts/{id}`. So if the file attaches to a Contract (option ii), contract CRUD UI is also greenfield.

---

## C. Reusable Attachment Infrastructure (C3, forced-download)

### C.1 The C3 announcement-attachment stack
- Table `announcement_attachment` — `V22__create_announcement_attachment.sql:25-42`: `id, announcement_id (FK ON DELETE CASCADE), object_key TEXT NOT NULL, content_type, size_bytes BIGINT, display_filename NOT NULL, created_by (FK users ON DELETE SET NULL), created_at`. Index on `announcement_id`.
- Entity `AnnouncementAttachment.java:41-85` (extends `CreatableEntity`; append-only, no update path).
- Repository `AnnouncementAttachmentRepository` (count / sumSizeBytes / findByAnnouncementIdOrderByCreatedAtAsc — used in caps + manifest).
- Authoring controller endpoints (`AnnouncementController.java`):
  - `POST /{id}/attachments` (multipart `file`) — `hasRole('ADMIN')`, `:330-339`.
  - `GET /{id}/attachments` (list) — `hasAnyRole('ADMIN','BOARD_MEMBER')`, `:349-356`.
  - `DELETE /{id}/attachments/{attachmentId}` — `hasRole('ADMIN')`, `:367-375`.
- Download contract: NO URL at upload/list time. The forced-download presigned URL is minted on the DETAIL read (`GET /api/announcements/{id}` → `attachments[].downloadUrl`), built by `buildAttachmentManifest` (`AnnouncementServiceImpl.java:319-342`).

### C.2 The four-layer forced-download security
1. **Magic-byte type allowlist (Tika on bytes, not header/extension):** `detectAttachmentMime(file)` against `ALLOWED_ATTACHMENT_MIME_TYPES = {pdf, docx, xlsx, pptx, txt}` (`AnnouncementServiceImpl.java:124-136`; rejects html/svg/csv). Called in upload `:830`.
2. **Signed `Content-Disposition: attachment` + `response-content-type=application/octet-stream`:** minted via `FileStorageService.presign(objectKey, contentDisposition, contentType)` (`FileStorageService.java:131-153`) using `ContentDispositionUtil.attachment(displayFilename)` (RFC 6266/5987 safe filename, `ContentDispositionUtil.java:42-47`) and `DOWNLOAD_RESPONSE_CONTENT_TYPE = "application/octet-stream"` (`AnnouncementServiceImpl.java:138-139`). Both overrides are folded into the SigV4 signature → untamperable. Applied at `:337-339`.
3. **Scope gate before any URL is minted:** `assertMediaPresignAccess(objectKey, principalId, role)` (`AnnouncementServiceImpl.java:382-409`) parses the owning id from the key (`parseAnnouncementIdFromKey`, `:421-435`), allows ADMIN/BOARD unrestricted, RESIDENT only if `existsReadableByResident`, everyone else denied; denial → empty manifest, never a leak (`buildAttachmentManifest:327-331`).
4. **Drafts-only + caps + coded 413:** upload requires a draft (`requireDraftForAttachment`, `:820`); per-file ≤10MB (`MAX_ATTACHMENT_FILE_BYTES`, `:122` → `ANNOUNCEMENT_ATTACHMENT_TOO_LARGE` `:824-826`), ≤5 files (`MAX_ATTACHMENTS_PER_ANNOUNCEMENT`, `:112`), ≤50MB total (`MAX_ATTACHMENT_TOTAL_BYTES`, `:115`), independent of image caps. Key convention `announcements/{id}/{uuid}{ext}` (`:846-847`).

### C.3 Servlet 413 / swallow config
`backend/src/main/resources/application.yml`: `spring.servlet.multipart.max-file-size=10MB` (`:34`), `max-request-size=55MB` (`:35`), and a **finite** `tomcat.max-swallow-size=60MB` (`:11`) — finite (not -1) so an oversize body past the edge cap is reset rather than fully buffered; the per-file app check yields a coded error before that.

### C.4 What is reusable AS-IS vs announcement-coupled
**Generic MinIO plumbing (in `common/storage` + `config`, NOT announcement-coupled — reuse verbatim):**
- `FileStorageService` (`FileStorageService.java:33-173`): `upload(key,stream,type,size)`, `presign(key)`, **`presign(key, contentDisposition, contentType)` (forced-download helper)**, `delete(key)`. Object-key-agnostic. Presign expiry `PRESIGN_EXPIRY_SECONDS=600` (`:43`).
- `ContentDispositionUtil.attachment(filename)` (`ContentDispositionUtil.java`): RFC-safe forced-download header. Generic.
- `MinioConfig` (`MinioConfig.java:24-101`): dual client — internal `minioClient` (byte ops) + public `minioPresignClient` (browser-host signature). Generic; one bucket.
- Cross-module proof of genericity: ticket photos and user avatars already use the same stack (grep `Minio|presign` hits `module/ticket/TicketServiceImpl.java`, `module/user/entity/User.java`, etc.).

**Announcement-coupled (must be re-authored, NOT reused directly):**
- `announcement_attachment` table + entity + repository (announcement FK).
- The scope gate `assertMediaPresignAccess` + `parseAnnouncementIdFromKey` (announcement-id-from-key, `existsReadableByResident`).
- Caps constants, Tika allowlist, key prefix (`AnnouncementService.MEDIA_KEY_PREFIX`), drafts-only rule, the manifest builder.

**Generic-but-ticket-coupled (note):** the HTTP endpoint `GET /api/files/presign` (`FileController.java:56-69`) routes through `ticketService.assertPresignAccess` only — it is NOT a general presign endpoint; a contract surface would need its own gate (matches §13 R-4).

> **LOCKED RULE honored:** do NOT extend `announcement_attachment`. A contract document needs its OWN table. A new table would **reuse** `FileStorageService` (incl. the forced-download presign), `ContentDispositionUtil`, `MinioConfig`, the Tika-on-bytes validation pattern, the caps pattern, and the after-commit MinIO cleanup pattern (`scheduleObjectCleanup` + `ObjectKeysObsoleteEvent`, `AnnouncementServiceImpl.java:983-987`); and **duplicate** (re-author for the contract owner) the table/entity/repository, the scope gate + key parser, and the caps/type constants.

---

## D. Dedicated Create/Edit Page + Save-First Pattern (C2.3b)

### D.1 How the announcement create page works
- `AnnouncementCreatePage.tsx` (replaced the retired create modal). **Save-first** (`:24-26` docstring; `submit(publishNow)` `:58-87`): "Lưu nháp" → `create.mutateAsync` → `navigate('/:id/edit')`; "Tạo & đăng" → create → publish → on publish failure the draft survives and the user is sent to edit with a notice (`:66-78`).
- **Lazy-save orchestrator** `ensureDraftThenUpload(uploadFn, kindLabel)` (`:107-148`): the upload managers are mounted on `/new` with no id yet; the FIRST upload validates the form → creates the draft → uploads → `navigate(replace, '/:id/edit')`. A synchronous `inFlight` ref (`:52`) guarantees exactly one draft under double-click. Invalid form → toast + inline error, no draft, no upload (`:111-119`).
- The managers (`AnnouncementMediaManager`, `AnnouncementAttachmentsManager`) are **id-driven**: pass `announcementId` + an optional `onLazyUpload`. On the edit page (id present) they upload directly; on `/new` (`onLazyUpload` provided) they hand the picked file to the orchestrator (`AnnouncementAttachmentsManager.tsx:44-122`, esp. `:113-116`). FE pre-checks size/total/count before handing off (`:101-109`).

### D.2 The save-first structural constraint
- **Uploads require an entity id first** (the upload endpoint is `/{id}/attachments`). There is no "create with files in one multipart submit" path. So a dedicated page that uploads a contract document **must** have either (a) an existing record id (edit-only), or (b) the lazy-save orchestration to mint the record on first upload.
- This applies identically whichever owner the file attaches to: no contractor/contract id → no object key → no upload.

### D.3 What transfers to a contractor (or contract) edit page
- The whole save-first + lazy-save scaffold is directly portable: a contractor `/new` page with a file field would need the same `ensureDraftThenUpload` logic IF uploads are allowed before the contractor exists; OR the page can be **edit-only for uploads** (create the contractor with plain fields first, then upload on the edit page where the id exists) — simpler, mirrors the constraint without the orchestrator. This is decision point 4.
- Reusable FE building blocks: the manager component shape (`onLazyUpload`, `externalBusy`, FE pre-checks, scheme-guarded `safeHref`, forced-download anchor `target=_blank rel=noopener`), and the create-page save-first skeleton.

---

## E. Permissions / IDOR / Visibility

- **Contractor CRUD:** writes (POST/PUT/DELETE) = `hasRole('ADMIN')`; reads (list/detail) = `hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER')` (`ContractorController.java:78,101,117,132,148`).
- **Contract CRUD:** create/update/`POST contracts` = `hasRole('ADMIN')`; contract detail read = `hasAnyRole('ADMIN','BOARD_MEMBER','TECHNICIAN')` (`:211-216`); contractor-nested contract list = `hasAnyRole('ADMIN','BOARD_MEMBER')` (`:168-180`); payments/schedules ADMIN-write, mixed reads (`:244-302`).
- **No RESIDENT surface for contractors or contracts** anywhere in `ContractorController`. Confirmed: RESIDENT does not appear in any contractor/contract `@PreAuthorize`. The admin route gates the page to ADMIN/BOARD_MEMBER (`App.tsx:74`). Expected end-state: residents never see contractors/contracts/contract docs.
- **Presign / IDOR:** the only live presign HTTP endpoint (`/api/files/presign`, `FileController.java:56-69`) gates strictly via ticket ownership — a `contracts/…` key would not match the ticket gate, so a resident cannot lift a contract doc through it. For a contract document surface, §13 R-4 (line 2515) is NORMATIVE: **staff-only (ADMIN/BOARD_MEMBER), MUST route through an `assertPresignAccess`-style ownership check before issuing URLs.** Any new endpoint must mint URLs only after an owner-scope check (mirror `assertMediaPresignAccess`), never trust a client-supplied key, and parse the owner id from the key so a malformed key → 403 not 500.

---

## F. Migration Sketch (proposal only — NOT executed)

Consistent with locked precedent (row-per-file table; new nullable columns; FK with safe on-delete; `created_by`/audit; **no table/column drops**). FK target depends on the B ruling:

- **If owner = Contract (option ii):**
  ```sql
  CREATE TABLE contract_document (
      id               UUID         NOT NULL DEFAULT gen_random_uuid(),
      contract_id      UUID         NOT NULL,
      object_key       TEXT         NOT NULL,
      content_type     VARCHAR(100),
      size_bytes       BIGINT,
      display_filename VARCHAR(255) NOT NULL,
      created_by       UUID,
      created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
      CONSTRAINT pk_contract_document PRIMARY KEY (id),
      CONSTRAINT fk_contract_document_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
      CONSTRAINT fk_contract_document_creator  FOREIGN KEY (created_by)  REFERENCES users(id)     ON DELETE SET NULL
  );
  CREATE INDEX idx_contract_document_contract_id ON contract_document (contract_id);
  ```
  (CASCADE mirrors `announcement_attachment`; the parent `contracts` row is RESTRICT-protected from contractor deletion already.)
- **If owner = Contractor (option i):** same table but `contractor_id` FK → `contractors(id)`. Because contractors are soft-deleted (never hard-deleted), `ON DELETE CASCADE` vs `SET NULL` is moot in practice; `RESTRICT` or `SET NULL` both safe. The task's suggested `ON DELETE SET NULL` fits a nullable FK; for a row-per-file child table CASCADE is more idiomatic — flag for the on-delete decision (point 7).
- **Reuse `attachment_url` instead (single-file, option ii-lite):** no migration at all — the column already exists (`V6:15`). Add `attachmentUrl` to `UpdateContractRequest` + a write path. Contradicts the row-per-file precedent (one file, replace-on-reupload). Listed for completeness; ties to decision points 2 & 7.
- New migration would be `V23__…`. No drops anywhere.

Next free version number: **V23** (latest is `V22`).

---

## G. API-SPEC Current State (verbatim anchors for the later phase)

- Contractor endpoints: `API-SPEC.md` §8, lines 1401-1491 (GET list `:1401`, POST `:1427`, GET detail `:1449` — note spec detail adds `activeContractsCount`/`totalTicketsAssigned`, **not** in the live `ContractorResponse` record — [TODO: verify spec-vs-code drift], PUT `:1477`, DELETE `:1485`).
- Planned contractor sub-resources: `GET /api/contractors/{id}/work-history` (PLANNED, `:1495`), `GET /api/contractors/{id}/contracts` (as-built list, `:1509`), `POST /api/contractors/{id}/contracts` (as-built create, `:1520`).
- Contract endpoints: `GET /api/contracts` (PLANNED, `:1542`), `GET /api/contracts/{id}` (`:1571` — spec shows `hasAttachment: true`, `totalPaid`, `schedulesCount`, `daysToExpiry` that are **not** all in the live `ContractResponse` record `ContractResponse.java:32-47` which instead exposes `attachmentUrl`, `createdBy`; [TODO: verify spec-vs-code drift]), `PUT /api/contracts/{id}` (`:1600`).
- **Contract attachment (the feature's BE half — already spec'd, NOT built):** `POST /api/contracts/{id}/attachment` (`:1608-1620`) and `GET /api/contracts/{id}/attachment` (`:1624-1639`). Both: ⚠️ NOT IMPLEMENTED + R-4. Spec says PDF-only, max 20 MB, single file ("upload or replace"), key `contracts/<id>/attachment/contract.pdf`.
- §13 file-surface access matrix: `:2503-2528`. Contract-attachment row `:2515` — "Staff only (ADMIN/BOARD_MEMBER) when built; MUST route through an `assertPresignAccess`-style ownership check before issuing URLs (R-4)."
- Announcement attachment manifest contract (the implemented forced-download reference): `:2043`, and notes `:2211`, `:2227`.

Any phase that builds this MUST update §8 (flip the two `attachment` endpoints from NOT IMPLEMENTED to as-built, or replace with the chosen table-backed design) and the §13 matrix row, in the same `docs(context)` commit group.

---

## CTO DECISION POINTS (each: recommendation + tradeoff — NOT decided)

1. **Attach point: Contractor vs existing Contract entity vs new record.**
   *Recommendation:* attach to the **Contract** entity (option ii) via a **new child table** (not the dormant `attachment_url`), because the domain already models contracts (B.1), the spec + §13 R-4 were designed for it (B.3/E), and it correctly supports a contractor's multiple contracts over time.
   *Tradeoff:* there is no contract CRUD UI today (B.5), so this expands FE scope beyond "a dedicated contractor page" — you'd also build contract create/edit/detail UI. If the CTO wants the smaller literal feature ("one PDF on the contractor card"), option (i) on Contractor is cheaper but under-models reality and orphans the existing Contract model.

2. **One file per owner or many? Append vs replace.**
   *Recommendation:* **many (row-per-file list)**, matching the locked C3 precedent (no single-key field).
   *Tradeoff:* the existing spec'd endpoints assume single "upload or replace" (B.3). Many-files is more flexible and reuses C3 patterns wholesale; single-file is less code and matches `attachment_url` but revives the anti-pattern C3 was created to retire.

3. **Allowed types + size caps.**
   *Recommendation:* **PDF-only** (the feature says "hợp đồng" = PDF; spec §8 says PDF-only) validated by **Tika magic-bytes**, reuse C3 caps (≤10MB/file; total cap if multi-file).
   *Tradeoff:* spec §8 contract attachment says max **20 MB** (vs C3's 10MB) — pick one; 20MB needs the multipart `max-file-size` (`application.yml:34`) raised and the swallow/edge caps re-checked. Allowing PDF+images widens UX but also the type allowlist + forced-download surface.

4. **Dedicated page scope: create+edit both, or edit-only? Is lazy-save on `/new` needed?**
   *Recommendation:* **edit-only uploads** — create the contractor (and/or contract) with plain fields first, then upload the document on the edit page where the id exists. Avoids porting the `ensureDraftThenUpload` orchestrator.
   *Tradeoff:* the announcement lazy-save UX (upload on `/new`) is nicer but is the most complex part of C2.3b (D.1). Edit-only respects the save-first constraint (D.2) with far less code; the cost is a two-step "save, then upload" flow for new records.

5. **Permission ruling: who manages contractors / uploads contract docs; resident visibility.**
   *Recommendation:* keep **ADMIN** for all writes incl. upload; **ADMIN/BOARD_MEMBER** for read/download (per §13 R-4); **no RESIDENT surface** (matches current state, E).
   *Tradeoff:* TECHNICIAN can currently read contractor/contract detail but the §13 matrix excludes them from contract-file download — confirm technicians are intentionally excluded from contract documents (likely yes; documents may contain commercial terms).

6. **Forced-download vs inline preview.**
   *Recommendation:* **forced-download**, identical to C3 (signed `Content-Disposition: attachment` + `application/octet-stream`), reusing `FileStorageService.presign(key, disp, type)` + `ContentDispositionUtil`.
   *Tradeoff:* inline PDF preview is friendlier for an admin reviewing a contract, but C3's locked ruling forbids inline (defense against renderable/script-capable payloads). If the CTO wants inline preview for PDFs specifically, that is a deliberate departure from the C3 rule and needs its own ruling.

7. **New table name + FK target + on-delete.**
   *Recommendation:* `contract_document` (or `contractor_document`, per point 1), FK to the chosen owner, **`ON DELETE CASCADE`** (mirrors `announcement_attachment` V22), plus after-commit MinIO cleanup of object keys (reuse `scheduleObjectCleanup`/`ObjectKeysObsoleteEvent`). `created_by` FK → users `ON DELETE SET NULL`. No drops.
   *Tradeoff:* the task suggested `ON DELETE SET NULL` for the FK; that fits only a nullable single-column-on-parent design (option ii-lite reusing `attachment_url`). For a row-per-file child table, CASCADE is idiomatic and keeps objects + rows consistent. The choice is coupled to points 1 & 2.

---

*End of investigation. Read-only — no application code modified, no decision made.*
