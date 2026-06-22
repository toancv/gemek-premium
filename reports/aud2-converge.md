# AUD.2 — Converge Contract + Announcement onto Spring Data Auditing (Option B1)

**Branch / HEAD:** `deploy/local` @ `0b1a924` (pre-commit).
**Tree state at start:** clean for code (untracked `reports/*` + `scripts/GenHash.java` only).
**Baseline:** full suite green sequentially — `BUILD SUCCESS`, 331 tests (exit 0) before any edit.
**Result:** full suite green — **336 / 336**, 0 failures, 0 errors. `BUILD SUCCESS`. (+5 new tests.)

---

## Scope delivered

Converged `contracts` + `announcements` off the manual `@ManyToOne User createdBy` +
`setCreatedBy(...)` model onto Spring Data JPA auditing (`@CreatedBy` / `@LastModifiedBy`
UUID via `AuditableEntity`), added `updated_by`, and reworked the response mappers to resolve
the creator display name from the UUID in **batch** (no N+1). `created_by_user_id` column
**kept** (CTO ruling — no rename). `AuditLogAspect` untouched (AUD.3).

---

## V18 migration

`V18__add_contract_announcement_updated_by.sql` (next free version; V17 was the last):

- `contracts`: `ADD COLUMN updated_by uuid` + `fk_contracts_updated_by → users(id) ON DELETE SET NULL`.
- `announcements`: `ADD COLUMN updated_by uuid` + `fk_announcements_updated_by → users(id) ON DELETE SET NULL`.
- **`created_by_user_id` NOT renamed.** No other table touched. Nullable + no backfill (system/seed writes have no actor).
- Applies cleanly (all integration tests boot Flyway against the dev DB — green).

## Entity changes

`Contract` and `Announcement`:

- Now `extends AuditableEntity` (which carries `@CreatedBy UUID createdBy` → col `created_by`,
  `@LastModifiedBy UUID updatedBy` → col `updated_by`, `@EntityListeners(AuditingEntityListener.class)`).
- **`@AttributeOverride(name = "createdBy", column = @Column(name = "created_by_user_id", updatable = false))`**
  remaps the inherited `createdBy` onto the EXISTING `created_by_user_id` column — field type changes
  (`User` → `UUID`) while the DB column name is preserved, as required.
- Removed the old `@ManyToOne(optional) User createdBy` field + its `@JoinColumn` + the `User` import.
- Manual `@PrePersist` / `@PreUpdate` timestamps (`created_at` / `updated_at`) kept as-is.

## Manual `setCreatedBy` removals — all entity-write sites gone (no double-write)

Grep ground truth: only **2** manual entity writes existed (not 3 — see note):

1. `ContractorServiceImpl#createContract` :259 → `contract.setCreatedBy(creator)` **removed**
   (plus the now-dead `userRepository.findById(principalId)` creator lookup).
2. `AnnouncementServiceImpl#createAnnouncement` :179 → `announcement.setCreatedBy(creator)` **removed**
   (plus the now-dead `findById(...).orElseThrow` creator lookup).

> **Note on the "third site":** the investigation report cited `AnnouncementServiceImpl:521`
> `.createdBy(creatorRef)` as a "second creation path". Ground truth: that line is the
> **response-DTO builder inside `toResponse(...)`**, i.e. the MAPPER — not an entity write.
> It was reworked (below), not a manual write to delete. A fresh
> `grep "setCreatedBy|\.createdBy\("` across `src/main` now returns **zero** entity-write sites.

Verified post-change: no remaining `setCreatedBy` on Contract/Announcement → no double-write
(auditing is the single writer; integration test asserts the column equals the principal and is
non-null after removal).

## Mapper batch-resolution (the N+1 risk — handled)

Response shape **preserved**: both DTOs still expose `createdBy : { id, fullName }` (FE reads
`createdBy.fullName`). API-SPEC lines 1499 / 1891 already match — **no API-SPEC change needed**.

- **Contract (MapStruct `ContractorMapper`):** `createdBy` now mapped `UUID → ContractResponse.UserRef`
  via a `default mapCreator(UUID, @Context Map<UUID,String> creatorNames)` method. The service
  (`listContracts` / `getContract` / `createContract` / `updateContract`) builds the name map ONCE
  per page via `creatorNames(Collection<Contract>)` → single `userRepository.findAllById(distinct ids)`
  → `id→fullName`, and threads it into the mapper. Generated `ContractorMapperImpl` confirmed:
  `createdBy = mapCreator(contract.getCreatedBy(), creatorNames)`.
- **Announcement (hand-written `toResponse`):** new `resolveCreatorNames(List<Announcement>)`
  (one `findAllById`); both list paths (RESIDENT + ADMIN) build it once per page and pass it to
  `toResponse(a, isRead, creatorNames)`; mutation/detail paths use a single-entry map. `updatedBy`
  is NOT exposed by either DTO → no modifier-name resolution needed.

## Scheduler ripple (createdBy now UUID, not User)

`ContractExpiryScheduler` + `MaintenanceScheduleRunner` notified the contract's creator via
`contract.getCreatedBy().getId()`. With `createdBy` now a UUID, simplified to `contract.getCreatedBy()`.
Null-skip guards unchanged. Stale "lazy createdBy" comment corrected.

## Tests

**New (TDD spec):**
- `ContractAnnouncementAuditConvergenceIntegrationTest` (real DB, `@Transactional`):
  - contract create → `created_by_user_id` == actor UUID via auditing (non-null, manual gone) + response `createdBy.fullName` resolved from UUID;
  - contract update under a 2nd actor → `updated_by` == modifier, `created_by` immutable;
  - announcement create → `created_by_user_id` == actor + response name resolved.
- `ContractorServiceImplTest#listContracts_resolvesCreatorNamesInBatch_noN1` — verifies
  `findAllById` called **once**, `findById` **never** for a 3-row page.
- `AnnouncementServiceImplTest#listAnnouncements_...noN1` — same guard on the ADMIN list path.

**Updated for the type change (compile + behavior):**
- `ContractorServiceImplTest` mapper stubs → `toContractResponse(any, any)`.
- `ContractExpirySchedulerTest`, `MaintenanceScheduleRunnerTest` → `setCreatedBy(UUID)` (dropped `new User()`).
- `ContractExpiryOnceOnlyTest` → authenticates as staff so auditing stamps `created_by` (manual set
  would be overwritten to null by `@CreatedBy` on persist).

## HTTP smoke

The integration test is the DB-backed smoke: it drives the real `ContractorService` /
`AnnouncementService` create paths against the dev Postgres, asserts `created_by_user_id` is set by
auditing AND the response shows the right creator name — the two facts a curl smoke would check,
but deterministic and in-suite. No separate app boot performed.

## Verify summary

| Check | Result |
|-------|--------|
| Tree clean before start | ✅ (code) |
| Baseline suite | ✅ 331, BUILD SUCCESS |
| V18 applies cleanly | ✅ (all integration tests boot Flyway) |
| All manual `setCreatedBy` removed (no double-write) | ✅ 2/2 entity sites, grep clean |
| `created_by_user_id` kept (no rename), field now `@CreatedBy UUID` | ✅ |
| Mapper resolves creator name in BATCH (no N+1) | ✅ both modules, guarded by tests |
| Response shape preserved / API-SPEC | ✅ unchanged, no edit |
| Full suite | ✅ **336 / 336**, BUILD SUCCESS |
