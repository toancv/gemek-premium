# Contractor STATUS (②) + Contract dates/FE (③) — READ-ONLY Investigation

> **Type:** READ-ONLY fact-finding. No application code changed. No rulings made.
> **Branch:** `feature/contractor-contracts-investigation` (cut from `main` @ `be6de3b`).
> **Investigation HEAD basis:** `main`. Contractor FE create/edit pages (P2) live on the
> unmerged `feature/contractor-contract-upload` branch — read for FE-placement context only;
> BE/schema facts below are from `main`.
> Every claim cites `file:line`. `[TODO: verify]` marks anything not directly confirmed.

---

## 0. Ground truth reconciliation

- `git log --oneline` HEAD on the working branch before cut = `a221f0b docs(context): PROGRESS P3b done; contractor-documents FEATURE-COMPLETE…` on `feature/contractor-contract-upload`.
- This reconciles to PROGRESS.md top snapshot ("CONTRACTOR DOCUMENTS — FEATURE-COMPLETE pending CTO :80 smoke. FE P3b DONE … `90ae857`/`f14c657`"). **No contradiction** between git and PROGRESS.
- Investigation branch cut from `main` (`be6de3b`); HANDOFF resume pointer ignored per instructions.
- **Migration ceiling on `main` = V22.** `V23__create_contractor_document.sql` exists only under `backend/target/classes/...` (stale compiled artifact) — NOT under `backend/src/main/resources/db/migration/` on `main`. It lives on the unmerged feature branch. **Numbering hazard:** any new migration authored against `main` would be `V23`, but that number is already claimed by the unmerged contractor-document branch → after that branch merges, a new status migration must renumber to **V24**. See §②-D / §③-J.

---

# PART ② — CONTRACTOR STATUS

## ②A. Where is "status" on the Contractor entity? — **IT ISN'T.**

**HEADLINE FINDING:** There is **no `status` field of any kind** on the Contractor entity. The
only state flag is a single **`boolean active`** (soft-delete / availability). The requested
three-value admin status (`active / blacklisted / suspended`) is **net-new** — it is NOT a
re-wire of an existing field.

| Aspect | Reality | Evidence |
|---|---|---|
| Field | `private boolean active = true;` | `backend/src/main/java/vn/vtit/gemek/module/contractor/entity/Contractor.java:92-93` |
| DB column | `is_active BOOLEAN NOT NULL DEFAULT TRUE` | `backend/src/main/resources/db/migration/V5__create_contractors_and_tickets.sql:16` |
| Index | `idx_contractors_is_active` | `V5:22` |
| Enum? | **No.** No `contractor_status` enum exists. | (no migration defines one) |
| Type | boolean (two-state) | as above |
| Default | `TRUE` (field init + DDL default) | `Contractor.java:93`, `V5:16` |

**Do not confuse with `ContractStatus`** — that enum (`PENDING / ACTIVE / EXPIRED / TERMINATED`)
belongs to the **Contract** entity, not the contractor:
`backend/src/main/java/vn/vtit/gemek/module/contractor/entity/ContractStatus.java:10-19`.

Adjacent contractor field that **IS** derived (for contrast, not the subject): `rating` is
recomputed from completed-ticket ratings — `Contractor.java:80-85`, recalculated at
`backend/src/main/java/vn/vtit/gemek/module/ticket/TicketServiceImpl.java:741-744`
(`contractorRepository.recalculateRating(...)`).

## ②B. STORED vs DERIVED — **STORED. Not derived. Not eligibility-coupled.**

### Writers of `active`
- **Create:** `createContractor(...)` never sets `active` → defaults `true` via field init + DDL.
  `ContractorServiceImpl.java:133-148` (no `setActive` call).
- **Update:** `updateContractor(...)` does **not** touch `active` at all (no field for it in the
  update path). `ContractorServiceImpl.java:166-200`.
- **Deactivate (the only mutator):** `deactivateContractor(...)` → `contractor.setActive(false)`.
  `ContractorServiceImpl.java:207-213`. Exposed as `DELETE /api/contractors/{id}` (ADMIN).
- **No recompute / no scheduler / no seed** sets `active` from other data. `active` is never
  derived. (No contractor seed migration exists; only `V2__seed_admin.sql`.)

> `grep` over the contractor module for active-writers — the only `setActive(false)` is the
> deactivate path (`ContractorServiceImpl.java:210`); `setActive(true)` at `:373` is on a
> **MaintenanceSchedule**, not a contractor.

### Readers / consumers of `active`
- **List filter param only:** `GET /api/contractors?active=` → `ContractorController.java:84`,
  applied as a JPA `Specification` equality at `ContractorServiceImpl.java:117-118`
  (`cb.equal(root.get("active"), active)`). The filter is optional; `null` returns all.
- **FE badge (display):** `ContractorsPage.tsx:72` renders `c.isActive` as a green/grey pill.
- **Mapper:** surfaced in `ContractorResponse` — `ContractorMapper.java:38,95`.

### Eligibility coupling — **NONE found**
- **Ticket → contractor assignment does NOT gate on `active`:** the assign path loads the
  contractor by id and assigns it with no active/status check.
  `TicketServiceImpl.java:456-460` (`contractorRepository.findById(...).orElseThrow(...)` →
  `ticket.setAssignedToContractor(contractor)`). The only assignment rules are
  mutual-exclusivity and the `MAINTENANCE_REPAIR` category guard
  (`TicketServiceImpl.java:429-438`; DB CHECK `chk_tickets_contractor_category` `V5:50-52`).
- **Contract creation → contractor:** `createContract(...)` loads the contractor by id; no
  evidence it gates on `active`. `[TODO: verify]` — not line-read this pass, but no
  active-predicate appears in any contractor-module reader grep.

### ②B verdict (decisive)
`active` is **STORED**, **admin-managed**, **never derived**, and **not consumed by any
eligibility gate** — only by an optional list filter and a display badge. **This is NOT a
STOP condition.** A manual status field would not desync any derived logic, because there is
no derived logic. The real consequence is scope, not risk: **the CTO's chosen "admin-manual
status" requires a brand-new column/enum** — see ②D.

## ②C. FE today — status is **display-only**

- List badge: `ContractorsPage.tsx:72` — `c.isActive ? ACTIVE : INACTIVE`, read-only pill.
- Create/edit **modal has no active/status control** — the form fields are companyName,
  contactPerson, phone, email, specialty, address, taxCode, notes only
  (`ContractorsPage.tsx:94-113`). `active` can only be flipped to `false` via the
  deactivate/DELETE action; it is never set back to `true` from the UI. Confirmed display-only.
- **Field-name note `[TODO: verify]`:** the DTO record component is `active`
  (`ContractorResponse.java:40`) but FE reads `c.isActive` (`ContractorsPage.tsx:72`). Java
  records serialize a boolean component named `active` as JSON `"active"` (no `is`-prefix
  convention), so `c.isActive` may resolve `undefined` (→ always "INACTIVE"). Possibly a
  pre-existing serialization/labeling bug OR a `@JsonProperty` exists somewhere not read this
  pass. **Out of investigation scope** — flagged only because ② will touch this surface.

## ②D. Decision points (enumerated; recommendation only — NOT decided)

1. **Value set + VN terminology.** Recommend an enum `ACTIVE / SUSPENDED / BLACKLISTED`
   (English identifiers per convention) with VN labels via the existing `enumLabels.ts`
   mechanism. **Sub-decision:** keep the legacy `is_active` boolean *and* add `status`, or
   replace? Recommend **add a new `status` enum and retire `is_active` as the source of truth**
   (map old `false`→`SUSPENDED` on backfill) to avoid two overlapping flags — but this is a
   data-model choice for the CTO.
2. **Does `BLACKLISTED`/`SUSPENDED` BLOCK downstream use?** Today nothing gates on contractor
   state (②B). Recommend **display-only first** (no eligibility enforcement) to match the
   current zero-coupling reality; if blocking is wanted, it becomes a *new* gate in
   `TicketServiceImpl` assign (`:456-460`) and in `createContract` — a larger change. CTO to rule.
3. **Audit on status change.** The "locked pattern" is **Spring Data JPA auditing**, not an
   audit-log emit: `AuditableEntity` auto-populates `updated_by` (actor UUID) on every update
   via `SecurityAuditorAware` (`AuditableEntity.java:36-45`), and `contractors` already has the
   `created_by`/`updated_by` columns (`V17__add_audit_columns.sql:50-53`). The standalone
   `audit_logs` table is **write-idle** since AUD.3 (former `AuditLogAspect` writer removed —
   `common/audit/repository/AuditLogRepository.java:15`, `common/audit/entity/AuditLog.java:32`).
   **So a status PATCH gets actor attribution for free** via `updated_by`; no separate
   after-commit emit exists to replicate. CTO to confirm that's sufficient.
4. **Permission.** Recommend ADMIN-only, mirroring every other contractor write
   (`ContractorController.java:102,133,149` all `hasRole('ADMIN')`).
5. **Endpoint shape.** Recommend `PATCH /api/contractors/{id}/status` with body
   `{ "status": "BLACKLISTED" }`; actor is **server-derived** via `SecurityAuditorAware`
   (no client identity in body) — consistent with the locked auditing pattern. Alternative:
   fold `status` into the existing `PUT /api/contractors/{id}` update body. CTO to choose
   dedicated PATCH vs PUT-field.
6. **Migration.** New enum + column needs a migration — see numbering hazard in §0
   (`V23` on `main` today, but must become `V24` after the contractor-document branch merges).
   Precedent shape for nullable actor/enum columns: `V17`. CTO/PM to sequence relative to the
   pending PR.

---

# PART ③ — CONTRACT ENTITY + DATES + FE

## ③E. Contract entity schema — **both dates already exist; NO migration needed**

Full field set — `backend/src/main/java/vn/vtit/gemek/module/contractor/entity/Contract.java`:

| Field | Type / constraint | Line | DB (`V6`) |
|---|---|---|---|
| `id` | UUID PK | `54-56` | `V6:6` |
| `contractor` | `@ManyToOne(LAZY, optional=false)` FK `contractor_id` NOT NULL | `62-64` | `V6:7,21` (`ON DELETE RESTRICT`) |
| `title` | `VARCHAR(255)` NOT NULL | `67-68` | `V6:8` |
| `scope` | TEXT | `71-72` | `V6:9` |
| `contractValue` | `NUMERIC(18,2)` (nullable) | `75-76` | `V6:10` |
| `currency` | `VARCHAR(3)` NOT NULL default `VND` | `79-80` | `V6:11` |
| **`startDate`** | **`LocalDate` NOT NULL** | **`83-84`** | **`V6:12` (`DATE NOT NULL`)** |
| **`endDate`** | **`LocalDate` nullable** (open-ended) | **`87-88`** | **`V6:13` (`DATE`)** |
| `status` | `ContractStatus` enum, NOT NULL default `PENDING` | `94-97` | `V6:14` |
| `attachmentUrl` | `VARCHAR(1000)` (superseded — see ③H) | `99-101` | `V6:15` |
| `notes` | TEXT | `104-105` | `V6:16` |
| `expiryNotifiedAt` | `OffsetDateTime` (expiry sent-marker) | `112-113` | (added by `V16`) |
| `createdAt`/`updatedAt` | timestamps via `@PrePersist`/`@PreUpdate` | `116-121` | `V6:18-19` |
| `createdBy`/`updatedBy` | actor UUIDs via `AuditableEntity` (override `created_by_user_id`) | class `49`, `AuditableEntity.java:38-45` | `V6:17` + `V18` |

**DATE VERDICT:** **BOTH `start_date` (NOT NULL) and `end_date` (nullable) already exist** in
entity, migration, and `DB-SCHEMA.sql`. There is a DB CHECK `chk_contracts_dates` enforcing
`end_date IS NULL OR end_date > start_date` (`V6:23`). **No migration is needed to add any
contract date.** ③ is purely an FE build on existing BE.

**Relationships / create-time dependencies:**
- `contract_payments` → `contracts` FK `ON DELETE RESTRICT` (`V6:30-44`). Children point *up*
  to contract; contract has no NOT-NULL dependency on payments.
- `maintenance_schedules` → `contracts` FK `ON DELETE RESTRICT` (`V6:47-63`). Same direction.
- **A contract can be created standalone** — only `contractor_id`, `title`, `start_date`,
  `currency`, `status` are NOT NULL, all settable at create. **Minimal header create is
  trivial; NOT a STOP condition** (no required child rows).

## ③F. Existing Contract API — **CRUD already exists (minus DELETE); not just the report**

All in `backend/src/main/java/vn/vtit/gemek/module/contractor/ContractorController.java`:

| Method + path | Auth (`@PreAuthorize`) | Lines |
|---|---|---|
| `GET /api/contractors/{id}/contracts` (list, paged) | `ADMIN, BOARD_MEMBER` | `168-180` |
| `POST /api/contractors/{id}/contracts` (create) | `ADMIN` | `190-199` |
| `GET /api/contracts/{id}` (detail) | `ADMIN, BOARD_MEMBER, TECHNICIAN` | `211-216` |
| `PUT /api/contracts/{id}` (update) | `ADMIN` | `225-232` |
| `GET /api/contracts/{id}/payments` | `ADMIN` | `244-249` |
| `POST /api/contracts/{id}/payments` | `ADMIN` | `259-268` |
| `GET /api/contracts/{id}/schedules` | `ADMIN, TECHNICIAN` | `280-285` |
| `POST /api/contracts/{id}/schedules` | `ADMIN` | `294-302` |
| **`GET /api/reports/contracts-expiring`** (report) | `ADMIN, BOARD_MEMBER` | `ReportController.java:115-120` |

- **No `DELETE` for contracts** anywhere.
- **Expiring report source query:** `contractRepository.findActiveExpiringWithContractor(today,
  maxDate, ACTIVE)` keyed on **`end_date`** (`ContractRepository.java:68-79`); service computes
  `daysToExpiry` from `end_date` (`ReportServiceImpl.java:227-254`), `withinDays` default 90
  (`ReportController.java:119`). Also two dashboard KPI counts on `status='ACTIVE'`
  (`ContractRepository.java:90-108`) and the daily scheduler query `findExpiringBetween`
  (`:50-56`).
- **The premise "headless on FE" = BE has full CRUD; FE has nothing.** ③ is an FE build over
  endpoints that already exist; **no new BE endpoint is required for the minimal header FE.**

## ③G. Existing Contract FE — **greenfield (none)**

- **No `/contracts` route** of any kind — `frontend/apps/admin/src/App.tsx:64-89` lists every
  route; contractors is `path="contractors"` (ADMIN, BOARD_MEMBER) `:74`; there is no
  contracts route.
- **No contract page component** — repo-wide grep for contract in `frontend/**/*.{ts,tsx}`
  hits only: `ReportsPage.tsx` (the read-only expiring report), `DashboardPage.tsx` (KPI),
  enum/label/i18n/errorMessage utilities, and the contractor list page. **No contract CRUD UI.**
- **Reachability options (for ③J):** (a) a "Contracts" section on the contractor
  **detail/edit page** (contractor-scoped; aligns with the nested
  `/api/contractors/{id}/contracts` create/list surface), or (b) a **top-level `/contracts`
  page**. Note the contractor edit *page* (P2) is on the unmerged
  `feature/contractor-contract-upload` branch; on `main` the contractor surface is still the
  list page + modal (`ContractorsPage.tsx`).

## ③H. Superseded attachment surfaces — **OUT OF SCOPE (do not touch / do not revive)**

- `contracts.attachment_url` column exists (`Contract.java:99-101`, `V6:15`) and is exposed by
  `ContractResponse.attachmentUrl` (`ContractResponse.java:42`).
- The `POST/GET /api/contracts/{id}/attachment` endpoints are **spec'd but NOT IMPLEMENTED**
  (no controller code), gated R-4: `docs/API-SPEC.md:1608-1630` and the file-surface matrix
  `API-SPEC.md:2515`. **Leave entirely alone** per task scope.

## ③I. ContractResponse — spec-vs-code drift (reconciling the earlier `[TODO: verify]`)

**Actual `ContractResponse` (code, ground truth)** — `ContractResponse.java:32-63`:
`id, contractor{id, companyName}, title, scope, contractValue, currency, startDate, endDate,
status, attachmentUrl (raw MinIO key), notes, createdBy{id, fullName}, createdAt, updatedAt`.

**API-SPEC `GET /api/contracts/{id}` claims** (`API-SPEC.md:1576-1596`) — **drifts from code**:

| API-SPEC field | In code `ContractResponse`? |
|---|---|
| `contractor.specialty` | **No** — contractor ref is only `{id, companyName}` (`:55`) |
| `hasAttachment` (bool) | **No** — code returns raw `attachmentUrl` string (`:42`) |
| `totalPaid` | **No** (absent) |
| `schedulesCount` | **No** (absent) |
| (list) `daysToExpiry` (`API-SPEC.md:1563`) | **No** — absent in `ContractResponse` |

So the spec describes an **enriched** detail DTO that the code does not implement; the live DTO
is **lean**. **FE must be built to the actual lean `ContractResponse`, not the spec.** (Same
class of drift on the contractor side: `API-SPEC.md:1468-1471` claims `activeContractsCount` +
`totalTicketsAssigned` + `updatedAt`; `ContractorResponse.java:29-42` has none of these — only
`active` + `rating`.) Per CLAUDE.md API-SPEC-sync rule, whichever direction the CTO rules, the
spec must be reconciled in the build phase.

## ③J. Decision points (enumerated; recommendation only — NOT decided)

1. **FE depth.** Recommend **MINIMAL contract-header FE** — list + create + edit over
   `{contractor, title, startDate, endDate, contractValue, currency, status, notes}` — and
   **DEFER `contract_payments` + `maintenance_schedules` UI** (endpoints exist but add scope).
   Alternative: full module incl. payments/schedules. CTO to choose.
2. **Reachability.** Recommend contractor-scoped section on the contractor edit page
   (matches nested create/list endpoints). Alternative: top-level `/contracts` page. Note the
   edit page lands via the P2 branch — sequence ③ FE after that PR merges, or build the section
   to mount on it. CTO to rule on placement + sequencing.
3. **Derived "expired" indicator.** Recommend a **display-only, FE-derived** badge from
   `end_date` vs today (precedent: `daysToExpiry` in the expiring report,
   `ReportServiceImpl.java:237-238`). This is **separate from and independent of** the manual
   CONTRACTOR status in ②. CTO to confirm wanted.
4. **Create/edit page pattern reuse.** Recommend reusing the C2.3b announcement create/edit
   pattern (shared form hook + `*FormFields` + `getVnErrorMessage` inline + MutationCache toast)
   — the same pattern the P2 contractor pages adopt.
5. **Migration.** **None required for ③** (dates already exist). Only ② needs a migration.
6. **DTO reconciliation.** Build FE to the lean live `ContractResponse`; decide whether to
   *also* enrich the BE DTO (`hasAttachment`/`totalPaid`/`schedulesCount`/`daysToExpiry`) to
   match the spec, or trim the spec to the code. CTO to rule (drives whether ③ stays FE-only or
   touches BE).

---

## STOP-condition checklist
- ② status **STORED, not derived, not eligibility-coupled** → **no dangerous STOP**, but
  flagged prominently: the requested 3-value status is **net-new** (only a boolean exists today).
- ③ Contract **dates already exist**; children are `ON DELETE RESTRICT` with **no create-time
  NOT-NULL dependency** → minimal header FE is **trivial**, **not a STOP**.
- Read-only throughout; no application code changed; no decision points decided.

## Appendix — key raw evidence lines
```
Contractor.java:92-93     @Column(name="is_active",nullable=false)  private boolean active = true;
V5:16                     is_active  BOOLEAN  NOT NULL DEFAULT TRUE
ContractorServiceImpl:210 contractor.setActive(false);   // sole status mutator (deactivate)
ContractorServiceImpl:117-118  spec.and(cb.equal(root.get("active"), active));  // sole filter reader
TicketServiceImpl:456-460 contractorRepository.findById(...).orElseThrow → setAssignedToContractor  // NO active gate
Contract.java:83-84       @Column(name="start_date",nullable=false) private LocalDate startDate;
Contract.java:87-88       @Column(name="end_date")                  private LocalDate endDate;
V6:12-13                  start_date DATE NOT NULL ; end_date DATE
V6:23                     CHECK (end_date IS NULL OR end_date > start_date)
ContractorController:190-199  POST /api/contractors/{id}/contracts  hasRole('ADMIN')  (create exists)
ContractorController:225-232  PUT  /api/contracts/{id}              hasRole('ADMIN')  (update exists)
App.tsx:64-89             routes — NO /contracts route
V17:50-53                 ALTER TABLE contractors ADD created_by/updated_by (audit columns present)
AuditableEntity:38-45     @CreatedBy created_by ; @LastModifiedBy updated_by (server-derived actor)
AuditLogRepository:15     audit_logs table write-idle since AUD.3 (Aspect writer removed)
```
