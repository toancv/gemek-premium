# PROGRESS — Apartment Management System

## ⏸ P3 DONE — place-resident flow live; multi-residency CREATABLE end-to-end — awaiting CTO smoke (2026-06-23)

**P3 (move-in / return / add-concurrent, keyed by phone) DONE.** `POST /api/residents` now branches
server-side on phone — NEW (provision user+residency, today's behavior), REUSE (existing user → add a new
`residents` row + reactivate a disabled account, **enabled-only**), or `ALREADY_ACTIVE_IN_APARTMENT`. The old
`PHONE_ALREADY_EXISTS` hard block is GONE (the dead-end §C of the investigation removed). New read-only
`GET /api/residents/lookup?phone=&apartmentId=` (ADMIN) returns a branch status (`NEW` / `ACTIVE_ELSEWHERE` /
`MOVED_OUT` / `ALREADY_HERE`) + minimal PII (display name + active apartments). **Concurrent multi-residency is
now CREATABLE through the product** (active-elsewhere + `confirmReuse=true` → 2nd active residency in a different
apartment). Identity is server-derived on reuse (request identity/password IGNORED — IDOR-safe). New
`ErrorCode`s `REUSE_CONFIRMATION_REQUIRED` (409, body carries the matched user via
`ReuseConfirmationRequiredException` + a dedicated handler) and `ALREADY_ACTIVE_IN_APARTMENT` (409, VN
"Cư dân này đang ở căn hộ này rồi."). NEW-branch field validation (fullName/password/dateOfBirth + complexity)
moved from bean-validation into the service (the fields are branch-conditional — bean validation can't branch on
a DB phone lookup).

**Admin FE (:80) two-step UI:** add-resident modal leads with a phone input + "Kiểm tra" (lookup). `NEW` → full
new-resident form. Existing (`ACTIVE_ELSEWHERE`/`MOVED_OUT`) → matched banner + residency-only fields + an
explicit reuse confirm popup that submits `confirmReuse=true` (identity reused, not editable).
`ALREADY_ACTIVE_IN_APARTMENT` → inline VN error. New VN error keys added; success via `meta.successMessage`;
refetch `['residents']`. Admin `pnpm build` green (590 modules, tsc clean).

**Tests (feat-first green):** new `P3PlaceResidentIntegrationTest` (gemek_test, 6 — NEW, RETURNING reactivate +
identity-reuse + IDOR, ADD-CONCURRENT 2-active, confirmReuse=false confirmation-required, ALREADY_ACTIVE, lookup
statuses + minimal PII). `ResidentServiceImplTest` createResident block rewritten to the new contract (reuse /
already-active / confirmReuse reactivation; old PHONE_ALREADY_EXISTS case removed). **Full backend suite
379/379 green (0 fail / 0 err).** `/code-review` (high, 2 finder angles + verify): BE "phone-uniqueness removed →
500" finding **REFUTED** (non-race dups route to reuse; the only race path is caught by the existing
`DataIntegrityViolationException` → 409 handler — no 500, unique index holds); FE Enter-key-in-reuse-mode finding
**FIXED** (form submit now routes by branch). No Must-fix remaining.

**Reports:** `reports/p3-place-resident.md` (plan + rationale). Raw suite: `reports/p3-suite.raw.txt`.
**DECISIONS:** "Residency lifecycle — P3 place-resident flow (AS IMPLEMENTED)" + reactivate `[hoãn]` note +
conditional-validation note. **API-SPEC:** lookup endpoint + rewritten POST /residents contract.

**Resume pointer:** awaiting CTO smoke of **P3 (place-resident, multi-residency creatable end-to-end)**;
residency-lifecycle core COMPLETE — remaining: **amenity multi-residency real attribution rule [PLANNED]**
(primary-or-latest temporary), and **move-out admin UI item (d)** (already DONE — see sections below). Do NOT
start follow-ups until CTO smokes P3. CTO smoke = on :80, place an existing active resident's phone into a 2nd
apartment (reuse confirm) → both residencies active; place a moved-out phone → account re-enabled + new residency;
new phone → new user+residency.

---

## ⏸ P2 DONE — concurrent multi-residency ENABLED at the DB level — awaiting CTO smoke (2026-06-23)

**P2 (index relax) DONE.** `uq_residents_active_user` relaxed from single-active-per-user to
active-per-`(user, apartment)`. **Migration `V20__relax_uq_residents_active_user_per_apartment.sql`**
(index-only: `DROP INDEX` + `CREATE UNIQUE INDEX ... ON residents (user_id, apartment_id) WHERE
move_out_date IS NULL`, same name; NO data DML). Pre-flight dup-pair check on dev `gemek` = **empty** →
built without violation. Before → after live index def:
`(user_id) WHERE move_out_date IS NULL` → `(user_id, apartment_id) WHERE move_out_date IS NULL`.
Applied to dev `gemek` via the normal pipeline (`docker compose up -d --build backend` → Spring Flyway
migrate; `flyway_schema_history` V20 success=`t`; no `flyway clean`). DB-SCHEMA.sql + `Resident.java`
entity javadoc updated (single-active → per-(user,apartment)).

**Multi-residency now REAL (proven):** `MultiResidencyIntegrationTest` (gemek_test, `@Transactional`) —
2 active residencies in 2 different apartments persist via the real repository (the INSERT that
previously violated the index now succeeds); SAME (user, apartment) pair still rejected
(`DataIntegrityViolationException`, Postgres 23505); `/residents/me` returns both; ticket per-context
allows each resided apartment + denies a third (403) + household list shows both; announcement feed
no-duplicate; amenity primary-or-latest no-throw. **5/5 green; full backend suite 371/371 green
(0 fail / 0 err, 54 classes; 366 P1 baseline + 5).** `/code-review`: **APPROVED, no Must-fix**
(the `DROP INDEX IF EXISTS` nit deliberately NOT applied — V20 already ran, editing breaks its Flyway
checksum; safe because V4 always creates the index first). **Amenity attribution still `[PLANNED]`**
(primary-or-latest temporary; real rule pending CTO).

**Report:** `reports/p2-index-relax.md`. Raw suite: `reports/p2-suite.raw.txt`. DECISIONS: "Residency
lifecycle — P2 index relax (AS APPLIED)".

**Resume pointer:** awaiting CTO smoke of **P2 (multi-residency live)**; then **P3 — move-in/return flow**
(reuse-by-phone: existing user → REUSE + new `residents` row + reactivate disabled account + append
`resident_history`). Authoritative plan: `DECISIONS.md` phased plan + the P2 as-applied entry. Do NOT
start P3 until CTO smokes P2.

---

## ✅ P1 DONE (superseded by P2 above) — Residency-lifecycle findActiveByUserId sweep (2026-06-22)

**P1 (findActiveByUserId sweep) DONE** — every consumer of the singular `Optional`-returning
`findActiveByUserId` was made multi-residency-safe WHILE the index still enforces single-active, so P2 can
relax the index without `NonUniqueResultException`. Index NOT touched; single-residency behavior identical.
Per-surface semantics as IMPLEMENTED (CTO-ruled):
- `/residents/me` → **ALL**: `getMyResident` returns `List<ResidentResponse>` (new `findAllActiveByUserId`);
  empty = `200 []` (was 404). **Contract change** — API-SPEC §Residents updated; resident FE updated.
- Ticket guards (createTicket, uploadPhotos, enforceReadAccess, enforcePhotoAccess, isHouseholdMember) →
  **PER-CONTEXT** via new `existsActiveByUserIdAndApartmentId(principal, ticketApt)`.
- Ticket "mine" list scope + redaction → **ALL** via new `findActiveApartmentIdsByUserId` (apt.id IN set).
- Vehicle owns-check → **PER-CONTEXT**.
- Amenity booking/listBookings → **SAFE TEMPORARY [PLANNED]**: primary-or-latest residency (first of ordered
  `findAllActiveByUserId`). Real attribution rule pending CTO ruling. API-SPEC `[PLANNED]` note added.

`findActiveByUserId` retained with a `@deprecated` note (no production caller remains). Full backend suite
**366/366 green**; resident `tsc && vite build` green. `/code-review` (high): backend clean, no regressions
(FE break + 2 import-order + consistency-oracle migration all resolved; one pre-existing string-concat left).

**Reports:** `reports/p1-findactivebyuserid-sweep.md` (sweep plan, per-site before/after, /code-review,
RED→GREEN evidence); raw logs `reports/p1-{unit-green,suite-green,announcement-red,postreview}.raw.txt`.
Investigation: `reports/residency-lifecycle-investigation.md` (§A consumer table, §F assumptions).

**Phased plan:** **P0** docs reconcile (done) → **P1** sweep (done) → **P2** index-relax migration
(`uq_residents_active_user` → `(user_id, apartment_id) WHERE move_out_date IS NULL`) → **P3** move-in/return
reuse-by-phone flow. Hard order: sweep before index relax — satisfied.

**Resume pointer:** awaiting CTO smoke of P1; then **P2 — relax `uq_residents_active_user` to
`(user_id, apartment_id)` [migration gate]**. Do NOT relax the index or start P2 until CTO rules. Authoritative
plan: `DECISIONS.md` ("Residency lifecycle — CTO ruling…" + the P1 as-implemented entry).

---

## ✅ COMPLETE (pending CTO :80 smoke) — Apartment status LOCKDOWN: not client-settable on update + MAINTENANCE hidden in UI (2026-06-22)

**Report:** `reports/apartment-status-lockdown.md`. **DECISIONS:** "Occupancy fully derived + status not client-settable".

Finalizes the occupancy model (follows the derive-display and derive-filter fixes below):

- **(b) Backend — status no longer client-settable on update.** Removed the `status` field from
  `UpdateApartmentRequest` (DTO) and the `setStatus(request.status())` line in
  `ApartmentServiceImpl.updateApartment`. Occupancy (OCCUPIED/AVAILABLE) is fully derived and MAINTENANCE
  has no set flow, so **no status value is client-settable** — closing the desync hole where an admin could
  store a status that contradicts the derived display. Verified beforehand: the only writers of `status`
  were create (:144, hardcoded AVAILABLE — kept) and update (now removed). Apartments keep their stored
  status (AVAILABLE post-V19).
- **(a) Frontend — MAINTENANCE hidden.** `ApartmentsPage.tsx`: removed the MAINTENANCE option from the
  `?status=` filter dropdown (keeps All / Đã ở / Còn trống); edit-form status `<select>` replaced with a
  read-only derived display; `status` dropped from the update payload. Badge keeps the MAINTENANCE colour
  for graceful legacy rendering. **BE MAINTENANCE (resolver/enum/filter) untouched** — retained for re-enable.
- **Tests (TDD):** +1 guard `ApartmentServiceImplTest.updateApartment_doesNotChangeStoredStatus` (stored
  status preserved across update; other fields still applied). Adjusted
  `ApartmentStatusFilterIntegrationTest.setMaintenance` to persist MAINTENANCE via repo (the update endpoint
  no longer accepts status). **Backend suite 358→359/359 green.** Admin `tsc && vite build` green.
- **API-SPEC:** `PUT /api/apartments/{id}` no longer accepts `status`.
- **Deferred backlog:** *maintenance set flow* — no UI/endpoint sets MAINTENANCE; BE fully supports it.
  When needed, add a dedicated intentional set path (not a free status field) and unhide the UI filter.

**🔔 SMOKE (CTO, :80):** apartment filter shows only Đã ở / Còn trống / Tất cả; editing an apartment has no
editable status control (read-only "tự động theo cư dân") and cannot change occupancy; occupancy still
displays correctly (derived).

---

## ✅ COMPLETE (pending CTO :80 smoke) — Apartment occupancy now DERIVED, MAINTENANCE priority, 3 surfaces converged (2026-06-22)

**Reports:** `reports/apartment-occupancy-diagnosis.md` (root cause A) + `reports/apartment-occupancy-fix.md`. **DECISIONS:** "Apartment occupancy derived…".

**Fixed:** occupancy was a stored `apartments.status` enum never synced by any resident path → 1612/1622 occupied apartments wrongly showed AVAILABLE; dashboard (stored) and resident-report (derived) disagreed (10 vs 1622). Now occupancy is DERIVED via ONE rule `OccupancyResolver.effective(stored, hasActiveResident)`: MAINTENANCE (stored, manual) priority; else OCCUPIED if ≥1 active resident (`move_out_date IS NULL`); else AVAILABLE. `OCCUPIED` is derived-only, never stored.

**3 surfaces converged on the rule:** apartment list (`ApartmentServiceImpl.listApartments` — batch `findActiveByApartmentIdIn(pageIds)`, no N+1), apartment detail (derives from already-fetched residents, 0 new queries), dashboard + resident-report (both call the SAME `ReportServiceImpl.computeOccupancy(blockId)` → identical numbers). Removed `ApartmentRepository.countByStatus`. **Dev DB now: dashboard==report==1622 occupied / ≈71.5%** (was 10 vs 1622).

**Migration `V19`:** normalize 10 stored OCCUPIED→AVAILABLE (no 1612-row data-fix — derivation fixes those). Response `status` field name/type unchanged → **FE needs no change**.

**Tests (TDD):** +10 — `OccupancyResolverTest` (5), `ApartmentServiceImplTest` (+3 incl. N+1 guard), `ReportServiceImplTest` (new, 2 incl. convergence). **Backend suite 343→353/353 green.** API-SPEC updated (status now computed).

**✅ FILTER NOW RESOLVED (2026-06-22)** — see `reports/apartment-filter-fix.md` + DECISIONS "Apartment `?status=` filter derives effective status". The prior "deferred filter" caveat is RESOLVED, not deferred: `?status=` now derives effective status in SQL (`ApartmentRepository.findAllByEffectiveStatus`, MAINTENANCE bound as enum param + EXISTS on active residents), mirroring `OccupancyResolver`. CTO bug fixed (`?status=AVAILABLE` no longer returns occupied units). Count query derived from the SAME `@Query` → `total` matches rows. Filter↔display agreement test added (`ApartmentStatusFilterIntegrationTest`, 5). **Backend suite 353→358/358 green.** Dev-DB effective counts: OCCUPIED 1622 / AVAILABLE 647 / MAINTENANCE 0 / total 2269. API-SPEC caveat removed.

**🔔 SMOKE (CTO, :80):** Open an apartment with active residents → status shows OCCUPIED (not "còn trống"). Dashboard occupancyRate ≈ resident-report occupancyRate (~71.5%, both). An apartment flagged MAINTENANCE with residents still shows MAINTENANCE.

## ✅ COMPLETE (pending CTO :80 smoke) — Backlog (d) follow-up: move-out now conditionally deactivates the login account (2026-06-22)

**Report:** `reports/d-moveout-deactivate.md`. **DECISIONS:** "Move-out conditional user deactivation".

**Relationship (verified first):** Resident→User is `@ManyToOne` (`Resident.java:54-56`) — one user CAN have multiple resident rows; at most ONE active (partial unique index `uq_residents_active_user`, `Resident.java:36-38`). Resident→Apartment `@ManyToOne` (one resident = one apartment). Active = `moveOutDate == null`. Clean model, no blocker.

**Shipped (BE):** `ResidentServiceImpl.moveOut` — after existing move-out logic (set `moveOutDate`, clear `primaryContact`, append MOVED_OUT history), in the SAME `@Transactional` tx: if the moved-out resident has a linked user AND `residentRepository.existsActiveByUserId(userId)` is false (no other active residency), set `user.active = false` and save. Reused the existing repo guard query `existsActiveByUserId` (`ResidentRepository.java:62-67`). Deactivation flips `user.active` directly via the entity (mirrors `createResident`'s `user.setActive(true)` at line 157) rather than `UserServiceImpl.deactivateUser` — that method's `SELF_OPERATION_NOT_ALLOWED` guard would wrongly abort move-out and it has no token-revocation side-effect to preserve (see DECISIONS). Atomic: if deactivation throws, the whole move-out rolls back.

**Shipped (FE):** `ResidentsPage.tsx` move-out dialog copy changed from "KHÔNG khoá tài khoản đăng nhập" → "Tài khoản đăng nhập **sẽ bị khoá** nếu cư dân không còn cư trú ở căn hộ nào khác." (truthful to the new conditional BE effect).

**Tests (TDD):** 4 new `ResidentServiceImplTest` cases — deactivates when no other residency; stays active when another active residency exists (safe guard, multi-residency possible); no linked user → no-op; deactivation throws → propagates (rolls back). **Backend suite 339→343/343 green.** Admin `tsc && vite build` green (590 modules). /code-review (high): no findings. **API-SPEC updated** (`POST /residents/{id}/move-out` now documents the conditional deactivation + atomicity).

**🔔 SMOKE (CTO, :80):** Move out a resident whose user has only that residency → confirm dialog states account will be locked; after confirm, that user can no longer log in. (If a user ever had a 2nd active residency, they'd stay able to log in.)

## ✅ COMPLETE (pending CTO :80 smoke) — Backlog (d) Resident move-out ("Kết thúc cư trú") FE action (Option B) (2026-06-22)

**Reports:** `reports/d-moveout-build.md` (build) + `reports/d-moveout-ui.md` (BE-contract investigation). **Backlog (d): DONE** — BE endpoint already existed; this was the UI.

**CTO ruling: Option B** — date picker (default today, editable) + optional notes (the no-picker plan was dropped because `POST /residents/{id}/move-out` REQUIRES `moveOutDate @NotNull` in the body; BE does NOT stamp now() server-side).

**Surface:** No dedicated admin Resident DETAIL page/route exists — `/residents` (ADMIN-gated, `App.tsx:68`) → `ResidentsPage` (list + create modal) is the ONLY resident admin surface. Move-out action + moved-out state added there per-row (de-facto detail surface; logged in DECISIONS).

**Shipped:** `useMoveOutResident()` hook (`api/hooks.ts`) → `POST /residents/{id}/move-out` `{moveOutDate, notes?}`, `meta.successMessage` (top-right toast) + `skipErrorToast`, `onSuccess` invalidates `['residents']`. `ResidentsPage.tsx`: `ResidentItem.moveOutDate: string|null`; new "Trạng thái cư trú" column (colSpan 6→7) — non-null → badge «Đã chuyển đi · dd/MM/yyyy» (no button); null → "Kết thúc cư trú" button. Confirm dialog (real modal): `VNDatePicker` defaulted to today (`toISODateLocal(new Date())`, editable) + optional notes textarea + VN confirm copy stating the REAL effect (marks moved-out, clears primary-contact, does NOT block login) + irreversibility. Confirm → mutate; error inline via `getVnErrorMessage` (incl. `RESIDENT_ALREADY_MOVED_OUT`, already in shared map — no key added); success → toast + refetch flips row to badge.

**BE effect (documented):** sets `moveOutDate`, clears `primaryContact` flag, appends MOVED_OUT history. Does NOT deactivate account / block login. Re-trigger guarded (`RESIDENT_ALREADY_MOVED_OUT`). **No undo endpoint → irreversible from UI.** `moveOutDate` (pure ISO `yyyy-mm-dd`, no time component — backlog-(b) trap avoided) sent verbatim to BE `LocalDate`.

**Verify:** admin `tsc --noEmit` exit 0; `vite build` 590 modules exit 0. /code-review (medium): no findings.

**🔔 SMOKE (CTO, after `--build nginx`, admin :80, resident detail/list):** "Kết thúc cư trú" opens a dialog with today pre-filled (changeable) + optional notes; confirming sets the «Đã chuyển đi» badge + date and removes the button; `moveOutDate` persists as a pure date in the DB row; a second attempt is impossible (button gone); re-opening an already-moved-out resident shows the badge, no active button.

## ✅ AUD.3 DONE — AuditLogAspect + @Auditable removed; AUD chain (auditing rework) COMPLETE (2026-06-22)

**Report:** `reports/aud3-remove-aspect.md`. **Plan:** `reports/audit-columns-investigation.md` §5 + §C. **DECISIONS:** "AUD.3 — AuditLogAspect + @Auditable removed".

**Shipped (AUD.3):** Deleted `AuditLogAspect` (the `@Aspect` write path) + the `@Auditable` annotation type (4 usages in `UserServiceImpl` removed first, then the orphaned annotation file) + the dead import. Auditing is now **fully** on Spring Data `created_by`/`updated_by` (`@CreatedBy`/`@LastModifiedBy` + `SecurityAuditorAware`, from AUD.1/AUD.2). **KEPT write-idle (NOT dropped — destructive):** `audit_logs` table + `V10` migration untouched (historical rows preserved); `AuditLog` entity + `AuditLogRepository` retained for read access (javadoc on both now marks them write-idle). **Intentionally dropped per CTO ruling (knowing trade-off, not a regression):** reset-password actor attribution + full before/after change-history (the aspect's `audit_logs` rows). `created_by`/`updated_by` capture only the latest create/update actor, not a change log.

**Verify:** blast radius pre-confirmed by grep (aspect/`@Auditable` referenced by exactly the 5 §5 files; `AuditLogRepository` injected only by the aspect; aspect `SecurityContext` read self-contained — `SecurityAuditorAware` is the reader now). No test asserted on `audit_logs` rows (grep `src/test` = 0) → nothing to un-assert. Added 3 `UserServiceImplTest` unit tests proving the user actions still WORK aspect-free (`deactivateUser` valid, `resetPassword` valid + NOT_FOUND; create/update already covered). Baseline 336 → **full suite 339/339 green, BUILD SUCCESS**. Full-context `@SpringBootTest` (`UserControllerTest`) + `AuditingActorCaptureIntegrationTest` boot the real ApplicationContext clean → no orphaned aspect bean / AOP wiring (HTTP-layer smoke equivalent; no live stack needed). /code-review: clean, no findings. **API-SPEC: no change** (no endpoint touched). Flyway: no new migration in AUD.3.

**State:** **AUD chain (auditing rework) COMPLETE** — AUD.1 (foundation) → AUD.2 (Contract/Announcement converge) → AUD.3 (aspect removal). Auditing is consolidated on Spring Data `created_by`/`updated_by` system-wide. `audit_logs` retained write-idle (future drop = separate CTO decision).

## ✅ AUD.2 DONE — Contract/Announcement converged onto Spring Data auditing (2026-06-22)

**Report:** `reports/aud2-converge.md`. **Plan:** `reports/audit-columns-investigation.md` §2 + §B Option B1.

**Shipped (AUD.2):** `V18__add_contract_announcement_updated_by.sql` adds nullable `updated_by uuid` + FK `users(id) ON DELETE SET NULL` to `contracts` + `announcements` (`created_by_user_id` **NOT renamed** — CTO ruling). `Contract` + `Announcement` now `extends AuditableEntity` with `@AttributeOverride(createdBy → @Column("created_by_user_id", updatable=false))` — field type `User → UUID`, column name preserved; old `@ManyToOne User createdBy` removed. Manual `setCreatedBy` deleted at **both** entity-write sites (`ContractorServiceImpl:259`, `AnnouncementServiceImpl:179`) + their dead creator lookups → single writer (auditing), no double-write. (The report's cited "3rd site" `AnnouncementServiceImpl:521` was the response-DTO builder, not an entity write — reworked, not deleted.) Response mappers resolve creator `fullName` from the UUID in **batch**: MapStruct `ContractorMapper` via `mapCreator(UUID, @Context Map)` fed by `creatorNames(...)` (one `findAllById`/page); `AnnouncementServiceImpl.toResponse` via `resolveCreatorNames(...)` (one `findAllById`/page). Schedulers simplified (`getCreatedBy().getId()` → `getCreatedBy()`).

**Verify:** baseline 331 green → **full suite 336/336 green, BUILD SUCCESS** (+5 new tests: 3 auditing-convergence integration + 2 N+1 guards). Response shape `createdBy:{id,fullName}` **unchanged** → **API-SPEC: no change** (lines 1499/1891 already match). `AuditLogAspect` untouched (AUD.3).

**Next:** **AUD.3** — remove `AuditLogAspect` + `@Auditable` (4 usages + annotation file), keep `audit_logs` table write-idle; DECISIONS + API-SPEC if any creator/modifier field changes.

## ✅ TEST.1b DONE — suite order-independent; TEST.1 (isolation) COMPLETE (2026-06-22) — awaiting CTO go to resume AUD.2

**Report:** `reports/test1b-isolation.md`. Test-infra only — no production code, no auditing touched.

**What 1b did:** TEST.1a made the suite green *sequentially* via the isolated `gemek_test` DB (per-JVM Flyway clean). 1b makes it **order-independent**. Of the 21 non-`@Transactional` `@SpringBootTest` classes: **18 → `@Transactional` rollback** (safe + complete: production has NO `@Async`/`REQUIRES_NEW`/`AFTER_COMMIT`, so every MockMvc write happens in the test tx and rolls back; no class uses native/JdbcTemplate cross-connection reads); **1 (CorsIntegrationTest) needs none** (0 writes, OPTIONS only); **2 left committing by design** — `NotificationIntegrationTest`, `TicketLifecycleIntegrationTest` assert on **read-back after a bulk `@Modifying` UPDATE** (mark-read / contractor-rating-recalc) → rollback leaves L1 cache stale and would falsely fail; `@Transactional` would mask the real production path. Each has an in-code `NOTE (TEST.1b)`. Their committed rows are harmless (every count assertion is scoped to a unique in-test marker; per-JVM Flyway clean isolates across runs). Done in 4 verified clusters of 5.

**Robustness proof:** added `backend/src/test/resources/junit-platform.properties` → random class+method order (fresh seed each run). **Sequential 331/331; two randomized-order runs 331/331** (distinct orders) = order-independence proven. **Parallel** (one-off CLI, 4 ForkJoinPool threads, NOT committed) = 328 pass / **3 errors**, all `IllegalStateException: Cannot start new transaction without ending existing transaction` — Spring `@Transactional` test-tx is **not parallel-safe in one JVM/context** (hits a pre-existing `@Transactional` class too). **Known limit, framework-level, not data pollution.** Enabling real parallel = CTO call (forked JVMs or explicit-cleanup) — out of 1b scope.

**State:** TEST.1 (suite isolation + order-independence) **COMPLETE** — suite is a reliable safety net again. **AUD.2/AUD.3 still paused; awaiting CTO go to resume AUD.2.** Flagged-resistant classes: NotificationIntegrationTest, TicketLifecycleIntegrationTest (intentional, documented).

## ✅ AUD.1 DONE — Spring Data JPA auditing foundation (2026-06-18) — awaiting CTO go for AUD.2

**Authoritative plan:** `reports/audit-columns-investigation.md` (§1 entity table, §3 AuditorAware, §4 column type/FK, §A/§B design). **AUD.1 report:** `reports/aud1-jpa-auditing.md`.

**Shipped (AUD.1):** `V17__add_audit_columns.sql` adds nullable `uuid` actor columns + FK `users(id) ON DELETE SET NULL` to **17 tables** — BOTH `created_by`+`updated_by` on 12 mutable (users, blocks, apartments, residents, vehicles, contractors, maintenance_schedules, tickets, amenities, amenity_bookings, parking_slots, parking_assignments); `created_by` ONLY on 5 append-only (resident_history, contract_payments, guest_vehicles, notifications, notification_subscriptions). JPA: `JpaAuditingConfig` (`@EnableJpaAuditing(auditorAwareRef="auditorAware")`), `SecurityAuditorAware` (`@Component("auditorAware")` `AuditorAware<UUID>`, mirrors `AuditLogAspect:117-127`, empty when no `UserPrincipal`), base `@MappedSuperclass` `AuditableEntity` (both) + `CreatableEntity` (created_by only). 17 entities wired. Manual `@PrePersist`/`@PreUpdate` timestamps **untouched**.

**Verify:** Flyway V17 applied; new `AuditingActorCaptureIntegrationTest` 4/4 green (actor capture, null-actor no-NPE, append-only no-updatedBy, timestamp regression). Full suite 331: 189 pass / 142 fail / 0 err — all 142 are the **pre-existing** `login→401` admin-hash corruption (`reports/ADMIN-LOGIN-DIAGNOSIS.md`), proven by reverting the 17 entity edits → `UserControllerTest` still 4/4 fails. Zero non-401 failures. /code-review: Java + migration clean; 2 test-clarity nits fixed, 1 (phone-collision, LOW) deferred. **API-SPEC: no change** — actor columns internal, not yet exposed (AUD.2 concern).

**Untouched (by design):** Contract/Announcement entities + their migration (AUD.2 convergence); `AuditLogAspect` + `@Auditable` (AUD.3 removal).

**Phase plan:** AUD.1 ✅ (this) → **AUD.2** converge contracts/announcements (add `updated_by`, switch to `@CreatedBy`/`@LastModifiedBy`, delete manual `setCreatedBy`, fix response mappers, no `created_by_user_id` rename per CTO) → **AUD.3** remove `AuditLogAspect`+`@Auditable` (keep `audit_logs` write-idle).

## ✅ COMPLETE (pending CTO :80 smoke) — Backlog (c) BOARD_MEMBER FE/BE 403 mismatches RESOLVED (Direction A) + announcements read-only for BOARD (2026-06-18)

**Reports:** diagnosis `reports/c-boardmember-403-diagnosis.md` (authoritative mismatch list), fix `reports/c-boardmember-403-fix.md` (per-control table). FRONTEND ONLY — **zero BE `@PreAuthorize` changes**, no BOARD write capability granted. Closes the P2 STEP B-flagged item (`reports/c-p2-stepB-applied.md` §"In-page admin-only control audit").

**CTO ruling:** Direction A for all 6 write mismatches (BOARD_MEMBER = read/oversight → hide write controls on FE), + open `/announcements` to BOARD read-only.

**Role helper (NEW):** `src/lib/useRoleFlags.ts` → `useRoleFlags()` = `{role, isAdmin, isTechnician, isBoardMember}` from `authStore.user.role`. Replaces the ad-hoc inline `isTechnician` on TicketDetailPage; used by all 4 pages.

**6 write controls gated to their EXACT BE allowed set (not just "not BOARD"):**
- TicketDetailPage assign card → `isAdmin` (BE `/{id}/assign`=ADMIN); also kills the broken `GET /users` staff-picker for BOARD.
- TicketDetailPage status card → `isAdmin || isTechnician` (BE `/{id}/status`=ADMIN,TECHNICIAN) — **TECHNICIAN keeps it**.
- ApartmentsPage create + edit → `isAdmin` (BE POST/PUT=ADMIN).
- ContractorsPage add + edit → `isAdmin` (BE POST/PUT=ADMIN).

**#7 announcements read-only for BOARD:** `App.tsx` route + `Layout.tsx` nav `/announcements` → `[ADMIN,BOARD_MEMBER]`. AnnouncementsPage write controls (create button + per-row publish button) gated to `isAdmin` so opening the route creates NO new 403. BOARD gets list/status/paging only. BE announcement endpoints untouched.

**Verify:** admin `tsc --noEmit` exit 0; `vite build` ✓ 590 modules (was 588) exit 0. /code-review (high, full working-tree diff) → **no findings**. `homePathFor`/landing/P2 routing untouched (BOARD still lands `/dashboard`). NOT browser-verified.

**SMOKE (CTO, port 80 — admin app):** after `docker compose up -d --build nginx` — log in as **BOARD_MEMBER**: ticket detail shows NO assign + NO status card; apartments + contractors show NO add/edit; «Tin tức» nav visible, announcements list reads but NO create/publish. Log in as **ADMIN**: every control still present. Log in as **TECHNICIAN**: still has the ticket status card.

## ✅ COMPLETE (pending CTO :81 smoke) — Backlog (e) RESIDENT FE: profile EDITING on existing ProfilePage (2026-06-18)

**Report:** `reports/e-resident-profile-fe.md` (static trace). FRONTEND ONLY, resident app only — BE / admin untouched, no apartment editing. Investigation `reports/e-resident-profile-investigation.md` confirmed all 3 `/api/auth/me*` endpoints already work for RESIDENT (no BE change).

**Added — edit own fullName/phone/email on the EXISTING `apps/resident/src/pages/ProfilePage.tsx`** (`/profile`, bottom-tab «Cá nhân»). Additive: existing view card, change-password block, logout all intact.
- New hook `useUpdateOwnProfile` (`api/hooks.ts`) → `PUT /auth/me/profile`, `meta {skipErrorToast, successMessage:'Cập nhật thông tin thành công.'}`, `onSuccess → invalidateQueries(['me'])` — the **real `useMe` key is `['me']`** (`hooks.ts:12`). Mirrors `useChangePassword`.
- Edit form seeded from `useMe` via `useEffect` (re-seeds after refetch → no stale). Errors inline VN via `getVnErrorMessage` (PHONE_ALREADY_EXISTS / EMAIL_ALREADY_EXISTS / VALIDATION_ERROR). Empty email → `undefined` (BE null). Own unchanged phone → no confirm + BE self-exclusion → succeeds.
- **Phone-change confirm via `@gemek/ui` `Modal`** (resident had no confirm dialog before): fires IFF `phone.trim() !== me.phone`; fullName/email-only edits submit directly. Token NOT rotated (subject=UUID) → no logout on phone change.
- Toast stays **center** (existing `<Toaster />`, default position) via `meta.successMessage`. No top-right, no new Toaster. New `profile.*` i18n keys in resident `vi.ts`. Types `MeProfile`/`ApiError` — no `any`.

**Verify:** resident `tsc && vite build` green (584 modules, exit 0). /code-review (high, full diff) → **no findings**. NOT browser-verified.

**SMOKE (CTO, port 81 — resident app):** after `docker compose up -d --build nginx` (resident runs :81; admin :80) — a resident edits name/email (NO confirm) vs phone (Modal confirm "Bạn sắp đổi số điện thoại đăng nhập…"), sees a **center** success toast, the page reflects new name/phone/email **without re-login**, and wrong-current-password on the existing password block still errors in VN.

**(e) now covers admin + resident.**

## ✅ (e) FE follow-up (2026-06-18): admin header/sidebar user-name now links to `/profile` (`Layout.tsx`, header span + sidebar footer → `<Link to="/profile">`, all roles). tsc+vite green. Pending CTO :80 smoke.

## ✅ COMPLETE (pending CTO :80 smoke) — Backlog (e) FRONTEND: self-service profile page (2026-06-18)

**Report:** `reports/e-fe-profile-page.md`. FRONTEND ONLY — no BE / no authStore role-gate / no `homePathFor` change. All 3 endpoints pre-existed (verified): `GET /api/auth/me`, `PUT /api/auth/me/profile`, `PUT /api/auth/me/password`.

**Added — admin portal `/profile` page («Trang cá nhân»), reachable by ALL admin-portal roles incl. TECHNICIAN:**
- `src/pages/ProfilePage.tsx` (NEW) — three INDEPENDENT areas: (A) read-only view (fullName/phone/email/role — role NOT editable); (B) update profile `PUT /me/profile` (fullName/phone/email); (C) change password `PUT /me/password` (current+new+confirm). Update and password are separate forms + separate submits — never merged.
- Route: `App.tsx` `<Route path="profile" RequireRole [ADMIN,BOARD_MEMBER,TECHNICIAN]>`. Nav: `Layout.tsx` `/profile` item, same 3 roles. i18n `nav.profile`.
- **homePathFor UNTOUCHED** — technician still LANDS on `/tickets`; `/profile` is nav-reachable only, no redirect-loop.
- **Phone-change confirm** (ruling §C.4): real overlay gate (mirrors P1 ADMIN-confirm) fires IFF `phone.trim() !== me.phone`; email/fullName edits skip it.
- **Refetch-after-update:** `useUpdateOwnProfile.onSuccess` invalidates `['me']`; `doUpdateProfile` calls new `authStore.setUser` from the 200 body so sidebar/header name + login phone update. Token NOT rotated → no logout.
- Errors inline VN via `getVnErrorMessage`: `PHONE_ALREADY_EXISTS`→phone, `EMAIL_ALREADY_EXISTS`→email, `WRONG_CURRENT_PASSWORD`(422)→current pw, `PASSWORD_POLICY_VIOLATION`→new pw. Own unchanged phone → confirm-skip + BE self-exclusion → succeeds. Password fields cleared on success.
- New hooks `useMe`/`useUpdateOwnProfile`/`useChangeOwnPassword` (reuse `meta.successMessage`+`skipErrorToast`). New type `src/types/profile.ts` `MyProfile` (no `any`). New `authStore.setUser` (held-user only; no role-gate touch).

**Verify:** admin `tsc --noEmit` exit 0; `vite build` green (589 modules, 2.94s). /code-review (cavecrew-reviewer over diff) → **No issues.** NOT browser-verified.

**SMOKE (CTO, port 80):** after `docker compose up -d --build nginx` (FE-only rebuild) — each role (ADMIN/BOARD/TECHNICIAN) sees «Trang cá nhân» in nav and can open it; editing email (NO confirm) vs phone (confirm dialog) differs; wrong current pw → VN error, correct one succeeds; after phone change the page shows the new phone and the user stays logged in.

## ✅ COMPLETE — Backlog (e) BACKEND: self-service profile-update endpoint (2026-06-18)

**Report:** `reports/e-be-profile-endpoint.md` · smoke raw `reports/e-be-profile-smoke.raw.txt`. Closes the one BE gap from `reports/e-self-profile-investigation.md` (§B: self profile-update of fullName/phone/email was MISSING).

**Added — `PUT /api/auth/me/profile`** (authenticated, any role; no `@PreAuthorize` → `anyRequest().authenticated()`):
- New DTO `UpdateOwnProfileRequest` — ONLY `fullName`/`phone`/`email`; no `role`/`isActive`/`password`/`id`. Validation mirrors `CreateUserRequest`.
- `AuthService.updateOwnProfile` + impl: identity **server-derived** from `principal.getId()` (IDOR-safe, mirrors `getMe`/`changePassword`); `PhoneUtils.normalize`; phone + email uniqueness pre-checks **excluding the caller's own row** (`PHONE_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS`); mutates only the three fields; returns `UserDetailResponse` (same shape as `GET /me`).
- **Escalation guard:** `role`/`isActive`/`password` immutable here — record binding ignores smuggled JSON keys; service never reads/sets them. Proven by test #3 AND over-the-wire smoke (crafted `role=ADMIN`+`isActive=false` → role stays TECHNICIAN, isActive stays true).
- **Token unaffected:** subject = user UUID, so a phone/email change does not invalidate the access token (smoke: same token used for `GET /me` after change).
- `docs/API-SPEC.md` updated.

**Verify:** new `SelfProfileUpdateIntegrationTest` **8/8** (happy/IDOR/escalation-guard/phone-uniqueness+self-exclusion/email-uniqueness/malformed-phone/malformed-email); full backend suite **327/327 green, BUILD SUCCESS**. HTTP smoke through nginx:80 against rebuilt backend confirmed all three invariants.

**NEXT (not started):** FE profile page — admin portal `/profile` route (all-roles, the universal-access exception), nav entry, wire read (`GET /me`) + change-password (`PUT /me/password`, live) + this new `PUT /me/profile`. Mirror resident `ProfilePage` UX + UsersPage `PHONE_ALREADY_EXISTS` handling. See investigation §D. **Awaiting CTO go.**

## ✅ COMPLETE — Backlog (c): technician ticket stat-card role-split (2026-06-18)

**Report:** `reports/c-tech-card-rolesplit-fe.md`. Closes the technician stat-card semantics follow-up from `reports/c-tech-overdue-card-diagnosis.md` (CTO ruling: scope-correct, label/semantics fix — FRONTEND ONLY; no BE/`@PreAuthorize`/authStore/route-guard change).

**Applied (`TicketsPage.tsx` + `vi.ts`):**
- TECHNICIAN only: «Trễ hạn» card → «Trễ hạn của tôi», sourced `overdue=true & mine=true` (own overdue, not the 327:1 shared-NEW-queue 328); drill-down `?overdue=true&mine=true` (count == drilled list); «Phân công cho tôi» (mine) card hidden; single combined red chip clears overdue+mine together. Grid `grid-cols-5`.
- ADMIN/BOARD: unchanged byte-for-byte — «Trễ hạn» = `overdue=true` building-wide, drill `?overdue=true`, mine card visible, two independent chips, `grid-cols-6`.
- Status/category cards untouched (scope-correct per diagnosis).
- Role from `useAuthStore((s) => s.user?.role)` (same source as nav role-gate). New i18n key `dashboard.slaBreachedMine`.
- Verify: admin `tsc --noEmit` exit 0; vite build green (588 modules). /code-review 1🔴 fixed (chip guards `overdue || mine`). NOT browser-verified.

**SMOKE (CTO, port 80):** after `docker compose up -d --build nginx` (FE-only, rebuild not restart) — technician sees «Trễ hạn của tôi» ≈ their own overdue (≈1 for tested tech, not 328) and NO «Phân công cho tôi» card; ADMIN unchanged (building-wide «Trễ hạn», «Phân công cho tôi» present).

## ✅ COMPLETE — Form-Feedback Standardization (2026-06-10)

**Standard:** All forms → errors = VN inline by BE error CODE (never raw serverMsg); success = toast.

**Authoritative plan:** `reports/form-feedback-survey.md` — 27 forms audited, 26 deviating, 1 fixed pre-survey.

### What is DONE

**Foundation (BE + shared util):**
- BE: `ResidentServiceImpl` email-dup throws `EMAIL_ALREADY_EXISTS` not generic `CONFLICT` (e66b86e). Both dup paths symmetric.
- BE: 7 generic-CONFLICT spots → specific codes; 4 new `ErrorCode` entries (e604f8a). `reports/error-code-audit.md` has full list.
- Shared util: `getVnErrorMessage(errorCode?: string): string` in `@gemek/ui/src/lib/errorMessages.ts` — 22 codes mapped to VN, unknown → fallback. 26 tests green (00db804 + extensions).

**Cluster 1 — 5 forms standardized:**
Forms: admin Login, resident Login, resident Change Password, resident Book Amenity, resident Rate Ticket.
- Admin ResidentsPage create form: `PHONE_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS` → per-field inline VN (ea68b10).
- 5 forms: errors via `getVnErrorMessage(err?.response?.data?.error)`; success via MutationCache `meta.successMessage` or navigate (ecda711 + 80a0fff + b4d2889).
- Login 401-interceptor reload fix: both `apiClient` interceptors skip refresh+retry for `/auth/login` and `/auth/refresh` — business-logic 401 must not trigger token-refresh loop (b4d2889).
- `WRONG_CURRENT_PASSWORD` (422): added to BE (`ErrorCode` + `AuthServiceImpl`) and mapped in `getVnErrorMessage`. 422 bypasses 401 interceptor.
- `PASSWORD_POLICY_VIOLATION` (422): `@Pattern` removed from `ChangePasswordRequest.newPassword`; domain check moved to service layer; mapped in `getVnErrorMessage` (8a6ba52 + 48a6388).
- Change-password success toast: `useChangePassword` hook uses `meta: { successMessage: 'Đổi mật khẩu thành công.' }` → MutationCache fires toast. `skipSuccessToast` removed (48a6388).
- Toast CSS purge fix: resident `tailwind.config.js` now includes `../../packages/ui/src/**/*.{ts,tsx}` (c518623). CSS grew 15.19→17.50kB confirming Toast classes included.
- Toast positioning fix: `fixed right-4` anchors to viewport right edge; resident column is `max-w-md mx-auto` (448px) → toast outside frame on desktop. Fixed to `left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm` — centered over column on all widths (c4b3179).

**Auth state (confirmed stable):**
- Phone-as-login migration: COMPLETE (all 9 steps, see ✅ section below).
- Change-password hash integrity: NO corrupting path — both validations precede `setPasswordHash`; `@Transactional` rolls back on exception. Earlier corruption non-reproducible in current code (`reports/change-pw-integrity.md`).

### Cluster 1 Lessons (apply to clusters 2–5)

1. **Success toast = `meta.successMessage` via MutationCache**, NOT component-level `toast.success()`. Use `meta: { successMessage: 'VN message' }` in the mutation hook; MutationCache fires it automatically. Component-level `toast.success()` is also valid (singleton, reliable), but `meta.successMessage` is cleaner when message is fixed.
2. **Toast API:** call `toast.success(msg)` / `toast.error(msg)`. Never `toast({...})` — `toast` is an object, not a function.
3. **Toast positioning:** Toast container uses `fixed left-1/2 -translate-x-1/2` (viewport-centered). Do NOT revert to `fixed right-4` (viewport-right) — breaks resident narrow column. Do NOT add `position:relative` wrapper — fixed ignores it.
4. **Login success = navigate only.** No toast on successful login. All other mutations: success → toast.

## ⚠️ DEFERRED — Module 10 notification dispatch

**Full trace:** `reports/publish-notification-trace.md`

`AnnouncementServiceImpl.publishAnnouncement()` does NOT create notification rows — dispatch is a stub (class-level Javadoc: "full dispatch wired in Module 10"). `NotificationService.createNotification()` is fully implemented but never called from the publish path.

**Three secondary breaks that also need fixing in the same sprint:**
1. Bell unread badge: `useNotifications()` returns `PageResponse` (no `unreadCount`); `/notifications/unread-count` endpoint exists but is never called by resident Layout.
2. Announcement content not rendered: `AnnouncementsPage` (resident) shows title only — `a.content` not in JSX; no detail route.
3. Per-user `isRead` missing from `AnnouncementResponse`: DTO has `readByCount` (aggregate) but no individual `isRead`; unread highlight always fires.

**CTO decision required before implementation.** Options in trace report (Option A full fix ~4h, B partial, C defer).

---

### Cluster 2 — IN PROGRESS (2026-06-10)

**Authoritative plan:** `reports/form-feedback-survey.md`
**Done in cluster 3 so far:**
- ApartmentsPage (#8 Create Apartment, #9 Edit Apartment) — code landed eb2ece4, **AWAITING browser-verify**. No new ErrorCodes needed. Diagnosis: `reports/cluster3-apartments-diagnosis.md`. BE: 5/5 pass. FE: tsc+vite build clean. CONFLICT reuse noted (see diagnosis §4) — deferred.

**Done in cluster 4:**
- ContractorsPage (#10 Create Contractor, #11 Edit Contractor) — code landed 888aa4a, CTO smoke-verified on browser — OK. No new ErrorCodes. Diagnosis: `reports/cluster4-contractors-diagnosis.md`.

**Done in cluster 5:**
- ParkingPage (#13 Assign Parking Slot, #14 End Parking Assignment) — code landed b726f90, CTO smoke-verified on browser — OK. Diagnosis: `reports/cluster5-parking-admin-diagnosis.md`. #13: added `meta.successMessage` + VN inline error; #14: added `skipErrorToast: true` + inline error via `endError` state (success path untouched).

**Done in cluster 6:**
- TicketDetailPage (#15 Assign Ticket, #16 Update Status) + TicketsPage (#17 Create Ticket) — code landed 31f59b4, **smoke-verify pending**. Diagnosis: `reports/cluster6-tickets-admin-diagnosis.md`. #15: success toast + VN inline error (split `assignError` from shared `actionError` — bug fix: errors were rendering in wrong panel). #16: success toast + VN inline error + English strings removed. #17: VN inline error only (redirect unchanged). BE HTTP-verified: 12/12 pass.

**Done in cluster 7:**
- VehiclesPage (#18 Create Vehicle) — code landed 2741ff0, **smoke-verify pending**. Diagnosis: `reports/cluster7-vehicles-admin-diagnosis.md`. Success toast added; HTTP-409-status hardcode replaced by `getVnErrorMessage(err?.response?.data?.error)` — `LICENSE_PLATE_ALREADY_EXISTS` maps to "Biển số xe đã được đăng ký." via code (not status). BE HTTP-verified: 9/9 VehicleControllerTest pass. tsc + vite build clean.

**Admin form-feedback COMPLETE — forms #1–#18 all standardized.**

**Done in cluster 8 (2026-06-10):**
- AnnouncementsPage (#20 Mark Read) — intentional-silent comment added; no functional change (fire-and-forget UX, silent by design).
- MyBookingsPage (#22 Cancel Booking) — `getVnErrorMessage` inline error added; `handleCancel` async with VN confirm; success toast was already working via `meta.successMessage: 'Đã hủy đặt chỗ'`.
- MyTicketsPage (#23 Create Ticket) — `meta.successMessage: 'Đã gửi yêu cầu.'` added to hook; catch fixed to `getVnErrorMessage(err?.response?.data?.error)`.
- MyVehiclesPage (#24 Register Vehicle) — `meta.successMessage: 'Đã đăng ký phương tiện.'` added to hook; HTTP-409-status hardcode replaced with `getVnErrorMessage(err?.response?.data?.error)`.
- ParkingPage (#25 Log Guest Vehicle) — `meta.successMessage: 'Đã ghi nhận xe khách.'` added; catch + validation error → `getVnErrorMessage`; all English form strings translated to VN.
- Commit: 77b9cae. BE tests: 8/8 (ParkingControllerTest). Resident tsc+vite build clean.

**ALL 27 FORMS COMPLETE. Form-feedback standardization DONE.**

**Admin toast position fixed (0da5f4c):** `Toaster` gained optional `position` prop (`"center"` default | `"top-right"`). Admin passes `position="top-right"`; resident unchanged.

**Done in cluster 2 so far:**
- AnnouncementsPage (#6 Create Announcement, #7 Publish Announcement) — code landed ec3a2d8, CTO smoke-verified on browser — OK. Diagnosis: `reports/cluster2-announcements-diagnosis.md`. BE: 4/4 tests pass.
- AmenitiesPage (#2 Create Amenity, #3 Edit Amenity, #4 Approve Booking, #5 Reject Booking) — CTO smoke-verified on browser — OK.
  - FE form feedback: d171df5 — Create/Edit successMessage; Approve/Reject skipErrorToast + inline error areas
  - CONFLICT→specific-code split: 073a3bf (BE), 2bf2fa5 (BE tests), 72bc19f (ui map + tests), 51e6808 (API-SPEC)
    - `AMENITY_NAME_EXISTS` (create/edit dup name), `BOOKING_NOT_PENDING` (approve/reject non-pending)

## ⚠️ DEFERRED — Code-Split Candidates (batch pass later)

Generic codes reused for context-specific cases — surfacing as less-specific VN messages. Recommend dedicated codes; defer to one batched BE + ui pass.

| Operation | Current code | Case | Recommended |
|-----------|-------------|------|-------------|
| assignSlot (parking) | `CONFLICT` | slot status ≠ AVAILABLE | `SLOT_NOT_AVAILABLE` |
| assignSlot (parking) | `CONFLICT` | slot already has active assignment | `SLOT_ALREADY_ASSIGNED` |
| assignTicket | `VALIDATION_ERROR` | both assignedToUserId + assignedToContractorId set | `BOTH_ASSIGNEES_SET` |
| cancelBooking | `CONFLICT` | booking status ≠ PENDING | `BOOKING_NOT_CANCELLABLE` |
| cancelBooking | `CONFLICT` | booking date is in the past | `BOOKING_DATE_PAST` |

---

### What is REMAINING

Apply per-form: `getVnErrorMessage(err?.response?.data?.error)` for errors; `meta: { successMessage: 'VN msg' }` for success; remove raw `.message` echoing; remove English fallback strings.

**Resume pointer:** Form-feedback standardization COMPLETE (all 27 forms). Next on-deck: DEFERRED items (Module 10 notification dispatch, TEMP_HIDDEN_DEFERRED guards, code-split candidates above). CTO browser smoke-verify pending for clusters 6, 7, 8 (`docker compose up -d --build nginx`).

---

## ✅ TECH DEBT — Test Regressions (CLEARED 2026-06-10)

**Full inventory:** `reports/test-regression-inventory.md`
**Final report:** `reports/test-regression-final.md`

**Result: 244 run, 244 pass, 0 fail.**

All 16 classes fixed. Fix pattern: `ADMIN_EMAIL` → `ADMIN_PHONE = "0900000000"`, `ADMIN_PASSWORD = "GemekAdmin2026"`, add `phoneFromUid` helper, resident-create helpers use `phone`+`dateOfBirth` instead of `email`. Two assertion fixes: `UserControllerTest` search (position-based → existence check), `TicketControllerTest` rate-not-done (`CONFLICT` → `INVALID_STATUS_TRANSITION`), `ResidentControllerTest` dup-email (`CONFLICT` → `EMAIL_ALREADY_EXISTS`).

---

## ✅ COMPLETE — Phone-as-Login Migration (2026-06-08)

**Status:** All 9 steps complete.

**Authoritative plan:** `reports/phone-username-survey.md` section D (9-step table).

**Key commits:** 4b3f020 (PhoneUtils) · 41b90ca (V12 migration) · 3e59bbc (core BE auth) · e1e2d14 (seeder) · 0f34f24 (FE login) · 594fae2 (FE display) · 4cf2ce1 (resident normalize) · 4237cba (API-SPEC v2.1)

| Step | Task | Status | Commit |
|------|------|--------|--------|
| 1 | `PhoneUtils.java` — normalize + isValid + 35 unit tests | ✅ done | 4b3f020 |
| 2 | V12 migration — phone NOT NULL + UNIQUE, email nullable | ✅ done | 41b90ca |
| 3 | Core BE auth: `UserPrincipal` (phone field, getUsername→phone), `JwtTokenProvider` (CLAIM_PHONE), `LoginRequest` (phone field), `UserRepository` (findByPhone/existsByPhone), `LoginResponse.UserSummary` (phone field), `AuthServiceImpl` (findByPhone + normalize), `CreateUserRequest` (phone required, email optional), `UserServiceImpl` (existsByPhone guard) | ✅ done | 3e59bbc (feat) + 1ccce1b (test) |
| 4 | `AdminSeeder` — promote hardcoded `"0900000000"` to `${app.admin.phone:0900000000}`, apply `PhoneUtils.normalize()` | ✅ done | e1e2d14 (feat) + bb4fe47 (test) |
| 5 | Verify/update `CreateResidentRequest` + `ResidentServiceImpl` for phone on user creation | ✅ done | (fix + test commits below) |
| 6 | FE both apps — auth stores (phone field, login sig, POST body), both `LoginPage.tsx` (label/type/validation in Vietnamese) | ✅ done | 0f34f24 (feat) + 388ba90 (docs) |
| 7 | FE audit — Layout (both, already name+role only ✓), resident `ProfilePage.tsx` (phone primary + email secondary row), admin `ResidentsPage.tsx` (phone+email columns, `ResidentItem` type replacing `any`) | ✅ done | pending commit |
| 8 | `API-SPEC.md` — auth login, user create, resident create contracts | ✅ done | (docs commit below) |
| 9 | Extra tests — resident null-email regression, CreateUserRequest null-phone validation | ✅ done | (test commit below) |

**Resume pointer:** Read `reports/phone-username-survey.md` for full context, hidden couplings, and risk notes before starting step 3.

---

## ✅ COMPLETE — i18n Phase 1: Inventory (2026-06-10)

**Output:** `reports/i18n-inventory.md` — full categorized inventory of English UI strings needing Vietnamese translation across both React apps.

**Counts:**
- Admin app: ~247 TRANSLATE strings across 11 files. Top 3: ParkingPage (~38), AmenitiesPage (~37), ReportsPage (~33).
- Resident app: ~68 TRANSLATE strings across 8 files. Top 3: AmenitiesPage (~13), TicketDetailPage (~12), ProfilePage (~10).
- AMBIGUOUS: 10 strings requiring CTO ruling (BE enum values rendered as display labels — primarily ticket status/priority, vehicle types, apartment status, parking slot type/status, contractor specialties, and 'Created' null-oldStatus fallback).
- AnnouncementsPage (admin): 0 strings — fully Vietnamese, no work needed.

**Scope rules (locked):** IN = static JSX text nodes, placeholders, buttons, labels, nav, table headers, empty states, modal titles, tab names. OUT = `getVnErrorMessage` strings (already VN), enum `value=` attrs, variable names, code comments, already-VN strings.

---

## ⏸ IN PROGRESS — i18n Phase 2: Translation (RESIDENT + ADMIN APPS COMPLETE — all pages VN)

**Resume pointer (fresh session):** Read `reports/i18n-inventory.md` for full string list. Architecture locked in DECISIONS.md (2026-06-10 i18n entry). Terminology: user-facing "Ticket" = "Phản ánh", display only; create/submit verb = "Gửi phản ánh" (DECISIONS.md 2026-06-10).

**Resident cluster 2 COMPLETE (2026-06-10) — resident app fully VN:**
- Translated: AnnouncementsPage ('Thông báo', emptyYet 'thông báo', 'Everyone'→'Tất cả'), AmenitiesPage hidden-deferred ('Đặt tiện ích', 'Đặt {name}' interpolated, full booking form), ParkingPage ('Bãi xe', 'Chỗ đậu xe của tôi', Khu/Loại/Phương tiện/Thẻ/Từ labels, 'Slot' fallback→'Chỗ đậu').
- Terminology sweep: 'Gửi yêu cầu'→'Gửi phản ánh' + 'Loại yêu cầu'→'Loại phản ánh' (MyTicketsPage), modal 'Tạo phản ánh'→'Gửi phản ánh', 'Không thể tải yêu cầu hỗ trợ.'→'Không thể tải phản ánh.' (TicketDetailPage), useCreateTicket successMessage 'Đã gửi yêu cầu.'→'Đã gửi phản ánh.' (hooks.ts, text only). Grep confirms 0 "yêu cầu" left in resident src. Commit bd795b5.
- Verified: `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step (port 81; Amenities/Parking are TEMP_HIDDEN_DEFERRED, nav-hidden — verify via direct URL or note deferred).
- ~~Wording flag~~ RESOLVED 2026-06-10: CTO ruled Announcements = "Tin tức" everywhere, notification bell = "Thông báo" (DECISIONS.md). AnnouncementsPage title fixed → 'Tin tức'. Grep verified: no swaps in resident src. Commit cd784b1.

**Enum display-label maps BUILT (2026-06-10), NOT yet wired:**
- `@gemek/ui` `lib/enumLabels.ts`: 7 groups + `labelFor(enumType, key)` (raw-key fallback, null→''). Display only — raw enum keys stay in `value=`/filters/comparisons. 51/51 ui tests green. Commit 0c9e8d3.
- Wiring happens per-page during admin translation. Resident pages still render raw enums in a few spots (e.g. TicketDetail status/priority, MyTickets/MyBookings status chips, Parking type) — later cleanup pass adopts labelFor there.

**Admin cluster A1 COMPLETE (2026-06-11):**
- `apps/admin/src/i18n/vi.ts` created (nav/layout/dashboard/reports; `t = createT(vi, viShared)`). Layout + DashboardPage + ReportsPage fully VN. Commit a212a9f.
- labelFor wired (first adoption): Dashboard + Reports 'Phản ánh theo loại' category labels via labelFor('TicketCategory', cat) — replaced `cat.replace(/_/g,' ')`; Reports contracts Status chip via labelFor('ActiveStatus', c.status). Raw keys untouched in keys/logic/filters.
- TicketCategory group added to @gemek/ui enumLabels (5 keys, wording copied from resident create-form options). Commit cf29cb9 (extra feat(ui) commit, not in CTO list — kept package-commit separation).
- DashboardPage local `const t = data?.tickets` renamed → `tk` (shadowed i18n t(); internal var only, no display/API change).
- Verified: 51/51 ui tests + tsc + vite build green BOTH apps. NOT browser-verified — CTO step (port 80).
- Wording flag: contracts Status chip uses ActiveStatus map → ACTIVE shows 'Hoạt động'; for contracts 'Hiệu lực' may read better (summary card says 'Hợp đồng hiệu lực'). If CTO prefers, add ContractStatus group later.

**Admin cluster A2 COMPLETE (2026-06-11):**
- Translated: ApartmentsPage (title/filters/headers/badge/edit modal; status filter+select labels via labelFor('ApartmentStatus'), value= raw), ResidentsPage (title/search/headers; OWNER/TENANT badge via labelFor('ResidentType')), ContractorsPage (title/search/headers/modal; specialty cell+select via labelFor('ContractorSpecialty'), isActive badge via labelFor('ActiveStatus')). Pagination Tổng:/Trước/Sau via viShared. Commit 6b536fd.
- New enum groups (commit 567b4d6): ContractStatus uses REAL BE keys PENDING/'Chờ hiệu lực', ACTIVE/'Hiệu lực', EXPIRED/'Đã hết hạn', TERMINATED/'Đã chấm dứt' (CTO's INACTIVE does NOT exist in BE — verified vn.vtit.gemek.module.contractor.entity.ContractStatus); ResidentType OWNER/'Chủ sở hữu', TENANT/'Người thuê' (badge rendered raw, inventory miss). viShared += common.saving 'Đang lưu...', common.total 'Tổng:'.
- Reports expiring-contracts Status chip switched ActiveStatus→ContractStatus.
- Also: create-apartment modal 'Diện tích (sqm)'→'(m²)' for unit consistency.
- Verified: 51/51 ui tests + tsc + vite build green BOTH apps. NOT browser-verified — CTO step (port 80).
- Wording flags: ApartmentsPage pre-existing VN strings still say "block" ('Chọn block...', 'Vui lòng chọn block.') vs new 'Tòa' — needs terminology-sweep ruling. AddApartment create modal was already VN ('Thêm căn hộ mới', 'Tạo mới') — left as-is.

**Admin cluster A3 COMPLETE (2026-06-11):**
- TicketsPage: title 'Phản ánh', '+ Gửi phản ánh', filters (Tất cả loại/trạng thái + options via labelFor), headers Mã/Tiêu đề/Loại/Trạng thái/Phụ trách/Hạn SLA, chips via labelFor('TicketCategory'/'TicketStatus'), emptyFound 'phản ánh', modal 'Gửi phản ánh' (category/priority selects via labelFor, 'Đang gửi...'/'Gửi'). TicketCategory map already covered page keys — no ui commit needed.
- TicketDetailPage: loadError/back/labels VN, category/priority/status via labelFor, Photos→'Hình ảnh', Status History→'Lịch sử trạng thái', 'Created'→'Khởi tạo', 'by'→'bởi', '(chỉ MAINTENANCE_REPAIR)' hint→labelFor, update-status select switched to labelFor (DONE 'Hoàn thành'→'Hoàn tất' per locked map — flagged).
- block→'Tòa' sweep: ApartmentsPage (placeholder, validation), AnnouncementsPage (label, option, validation). Display-"block" grep in admin src = 0. Decision recorded in DECISIONS.md.
- Commit 9b2de7b. Verified: tsc + vite build green (admin). NOT browser-verified — CTO step (port 80).

**Admin cluster A4 COMPLETE (2026-06-11) — ADMIN APP FULLY VN (all pages):**
- ParkingPage: 'Bãi xe', tabs 'Chỗ đậu xe'/'Xe khách', filters (Tất cả loại/trạng thái + options via labelFor), slot headers Chỗ/Khu/Loại/Trạng thái/Phân cho/Thao tác, type cell + status chip via labelFor('VehicleType'/'ParkingSlotStatus'), emptyFound 'chỗ đậu xe', 'Phân công'/'Hủy phân công', guest headers Biển số/Chủ xe/Căn hộ tiếp/Giờ vào/Giờ ra/Mục đích, emptyYet 'xe khách', 'Đang trong bãi', assign modal 'Phân chỗ {slotNumber}' (interpolated) + labels/placeholders/'Đang phân...'.
- VehiclesPage: 'Phương tiện', '+ Thêm phương tiện', filters via labelFor (isActive filter values stay "true"/"false", labels ActiveStatus), headers, type cell + isActive badge via labelFor, emptyFound 'phương tiện', modal 'Thêm phương tiện' + type select labels via labelFor. VEHICLE_TYPES map param `t` renamed → `vt` (would shadow i18n t()).
- AmenitiesPage: 'Tiện ích', 'Thêm tiện ích', tabs 'Tiện ích'/'Lượt đặt chờ duyệt', headers, emptyFound 'tiện ích', Có/Không badge, booking headers, emptyYet 'lượt đặt', 'Duyệt'/'Từ chối', reject dialog 'Từ chối đặt chỗ'/'Lý do'/'Đang từ chối...', amenity modal 'Sửa tiện ích'/'Thêm tiện ích' + all labels, Hủy/Lưu/Đang lưu... via shared.
- No new enum keys needed (CAR/MOTORBIKE/BICYCLE/OTHER + AVAILABLE/OCCUPIED/RESERVED + ACTIVE/INACTIVE already mapped) — no feat(ui) commit. Enum value=/filters/logic untouched.
- Commit 0a66bfe. Verified: tsc + vite build green (admin); leftover-English grep on 3 pages = 0. NOT browser-verified — CTO step (port 80; Parking/Vehicles/Amenities may be TEMP_HIDDEN_DEFERRED — verify via direct URL).

**Admin leftover cleanup COMPLETE (2026-06-11):**
- `t('status')` key miss fixed (key is `common.status` → fallback rendered literal "status"): VehiclesPage:119 + ParkingPage:126 headers → `t('common.status')` = 'Trạng thái'.
- SLA wording (CTO-approved set): tickets.slaDeadline → 'Hạn hoàn thành'; dashboard.slaBreached + reports.slaBreachedCol → 'Trễ hạn'; reports.slaBreachRate → 'Tỷ lệ trễ hạn'; admin TicketDetailPage hardcoded 'SLA:' → new key ticketDetail.sla = 'Hạn hoàn thành:'. Grep 'SLA' in admin src = 0 displayed leftovers.
- "System Administrator" (top-right header + bottom-left sidebar of admin layout): NOT a static string — it is `user.fullName` from API (seeded admin account, backend AdminSeeder.java:91). Shows logged-in user identity → NOT removed. ⏸ CTO ruling pending (options: leave as-is / change seed fullName to VN / DB update of admin account). No FE change made for this item.
- Commit e7b945b. Verified: tsc + vite build green (admin). NOT browser-verified — CTO step (port 80).
- Date-format task (mm/dd→dd/mm) still pending — its OWN later session, untouched here.

**Resident enum-cleanup COMPLETE (2026-06-11) → i18n Phase 2 COMPLETE (both apps fully VN, enum labels consistent):**
- New maps in @gemek/ui enumLabels: AnnouncementType (GENERAL/URGENT/MAINTENANCE/AMENITY/EVENT → Chung/Khẩn cấp/Bảo trì/Tiện ích/Sự kiện), BookingStatus (PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED → Chờ duyệt/Đã duyệt/Bị từ chối/Đã hủy/Hoàn tất) + tests. Commit 649e8c9; ui 51/51 tests green.
- labelFor wired (display only; value=/chip-color keys/comparisons stay raw BE keys): HomePage + AnnouncementsPage announcement-type chips, MyBookingsPage status chip, MyTicketsPage status chip + category line (replace() hacks removed), TicketDetailPage status chip/category/priority + status-timeline old→new, ParkingPage slot type, MyVehiclesPage type options (map param t→vt, shadowed i18n t). Bonus leftover fixed: resident TicketDetail hardcoded 'SLA:' → ticketDetail.sla='Hạn hoàn thành:' (approved SLA mapping). Commit 3793983. tsc + vite build green (resident). NOT browser-verified — CTO step (port 81).
- Earlier resident raw-enum tech-debt note: CLEARED.
- ~~Leftover: admin AnnouncementsPage type options raw~~ FIXED 2026-06-11: type options via labelFor('AnnouncementType'); new AnnouncementScope map in @gemek/ui (ALL/BLOCK/FLOOR → Toàn bộ/Theo tòa/Theo tầng, BE-verified, commit fb42ae4, ui 51/51 green) wired to scope options + list "Phạm vi" column (was raw targetScope) — 'Theo block'→'Theo tòa' done via map. Commit 330aee0; admin build green. i18n fully COMPLETE both apps incl. dynamic form enums. NOT browser-verified — CTO step (port 80).
- "System Administrator" CTO ruling still pending (see admin leftover cleanup above).

**⏸ IN PROGRESS — date-INPUT picker rollout (KIND B → VNDatePicker), PILOT DONE 2 of 6, awaiting CTO pattern approval:**
- react-day-picker 9.7.0 (exact) added to @gemek/ui via corepack pnpm (pnpm 11.5.2; plain `pnpm` NOT on PATH — use `corepack pnpm`; npm install inside the pnpm tree FAILS, do not mix).
- `VNDatePicker` in @gemek/ui (commit c2cfe0a): value/onChange = ISO yyyy-mm-dd always; dd/mm/yyyy display; local-safe parseISODateLocal/toISODateLocal in dateFormat.ts (no UTC round-trip → no off-by-one); props min/disabled/placeholder/className; ui tests 65/65 green incl. month/year-boundary cases.
- Pilot (commit 8c4b8e7): admin Reports 'from' (controlled — value/onChange wired straight to existing ISO state; query param unchanged) + admin Residents moveInDate (was uncontrolled FormData → now controlled ISO state; payload key/shape/value format unchanged). Admin build green. NOT browser-verified — CTO step (port 80): check dd/mm display, Reports filter correctness, resident create saves moveInDate without off-by-one.
- Pattern APPROVED by CTO 2026-06-11 (pilot browser-verified: dd/mm display + ISO payload intact, no off-by-one).
- Rollout COMPLETE 2026-06-11 — all 6 date inputs now VNDatePicker: admin dateOfBirth (controlled, dobError clear kept; red-border error styling on the input itself dropped — wrapper has fixed classes, error TEXT below remains) + Reports 'to' (twin of 'from') + Parking startDate (FormData → controlled ISO state, reset in closeAssign) = commit 372f21a; resident AmenitiesPage bookingDate (FormData → controlled ISO state, reset on modal open, min=today ISO passed through → past dates disabled) = commit e58b892. ui 65/65 green; both builds green. KIND B limitation RESOLVED. NOT browser-verified — CTO step (ports 80/81).

**Admin AnnouncementsPage type-badge leftover FIXED (2026-06-11, commit 7038c1b):** list-table type badge rendered raw `{a.type}` → labelFor('AnnouncementType'). Map keys verified BE-exact (GENERAL/URGENT/MAINTENANCE/AMENITY/EVENT) — no map change needed. NOTE: 3rd dynamic-section miss on this one page (create-form options, scope column, now list badge) — static i18n inventory was blind to dynamically-rendered enum spots; any future i18n audit must grep for `{x.type|status|...}` render patterns, not just string literals. Admin build green. Browser-verify = CTO (port 80).

**✅ PUSHED 2026-06-11 — i18n + date-format work COMPLETE and on origin/deploy/local (HEAD 00a8cd2):** two verified pushes — b6078ba/3ce90c9 (work + docs) then 00a8cd2 (chore: gitignore node_modules/ + dist/, none were ever tracked). Pre-push verification green both times — ui 65/65, admin + resident tsc+vite builds green, backend full suite 244/244 (Java 21 via backend\mvnw.cmd — plain `mvn` NOT on PATH, use the wrapper).

**✅ Module 10 PHASE 1 (dispatch core) COMPLETE + CTO browser smoke-verified (2026-06-11).** Phase-1 design: `reports/module10-dispatch-design.md`. **EXTENDED scope (N1 deep-link/detail route → N2 rich content → N3 per-user event notifications → N4 ticket media+comments) recorded in `reports/module10-extended-backlog.md` — NEXT = N1.** N2/N4 gated behind F-05 presign fix; N3 design-first.
- Design proposal committed 96f9fa9; CTO approved P1 scope.
- **P1 DONE:** `ResidentRepository.findRecipientUserIds(scope, blockId, floor)` — typed default method → String-scoped backing `@Query` (Hibernate 6.5 enum-param anchoring limitation, see report P1-findings). Commit 221813b. Contract test `AnnouncementRecipientConsistencyTest` (4 tests, feed↔dispatch invariant per scope, edge cases moved-out/deactivated/no-apartment) commit a671c70. Suite 248/248 green.
- **P2 DONE (2026-06-11):** dispatch wired into `publishAnnouncement()` — CAS `publishIfDraft` (409 on already-published, race-safe, replaces idempotent-200), in-TX batch dispatch via `NotificationRepository.saveAll` + `getReferenceById` (no per-row SELECT), body "Có thông báo mới: {title}", `batch_size: 50` config. Commits: feat 22114b8, test 8880499 (`AnnouncementPublishDispatchTest` 5 tests: per-scope row counts, field checks, 409+no-duplicate, unread increment). Suite 253/253 green. DECISIONS.md entry 2026-06-11 Module 10 P2.
- **P4 DONE (2026-06-11):** per-user `isRead` on `AnnouncementResponse` (§E E-3 backend half) — `@JsonProperty("isRead")` field (mirrors NotificationResponse); `existsByAnnouncementIdAndUserId` already existed (no addition); list paths use ONE batched query/page (`AnnouncementReadRepository.findReadAnnouncementIds(userId, pageIds)` + in-memory set), detail uses single exists(); `toResponse(a)` kept as false-default overload for mutation paths (draft/just-published — read row impossible for caller). Commits: feat 0070727, test 3b5b725 (`AnnouncementIsReadTest` 3 tests: detail flip, mixed-page per-row flags, admin findAll path). Suite 256/256 green.
- **P5 DONE (2026-06-11) — Module 10 BE+FE code COMPLETE.** E-4: `useMarkAllRead` put→post both apps (no FE caller of `/notifications/{id}/read` exists; announcement markRead already POST) — commit dcfc42b. E-1: `useUnreadCount()` (`GET /notifications/unread-count`) both apps; both Layout.tsx badges read `unreadData.unreadCount` (old `notifData?.unreadCount` was dead — PageResponse has no such field); markAllRead invalidates `['unread-count']` + `['notifications']` — commit 732beac. E-2+E-3-FE: resident AnnouncementsPage expand-on-click shows `a.content` inline (local `expandedId` state, no route); isRead now real per-user (P4 BE), markRead already invalidates `['announcements']` so border clears on refetch; item type is `any` — no type to extend — commit 32bda65. Both apps tsc + vite build green. Resolves §G Q4 (unreadCount via dedicated endpoint, not page response), Q6 (inline expand), Q7 (FE→POST).
- **CTO browser smoke-test PASSED (2026-06-11):** bell badge increment on publish, panel list, News expand + border clear, mark-all-read no-405 — all verified. API-SPEC.md GET /api/notifications fixed: `unreadCount` removed from page response example (count comes from `GET /api/notifications/unread-count`).
- **N1 DONE (2026-06-11):** resident bell rows clickable — `useMarkNotificationRead` (invalidates notifications + unread-count) + deep-link via `NOTIF_ROUTES` referenceType map in Layout (Announcement → `/announcements/:id`; unknown type → mark-read only); new `AnnouncementDetailPage` + route (mark-read on first load = single read surface, P5 expand-in-card removed); `any` debt paid — `api/types.ts` AnnouncementItem + NotificationItem typed end-to-end. Admin bell untouched (deferred to N3 — admins receive no dispatch rows until then). Commits: refactor e26a965, feat d5e6b4f, feat b34628c. Resident tsc + vite green; admin untouched (no shared pkg change). NOT browser-verified — CTO step.
- **N3 design APPROVED (2026-06-11):** `reports/n3-event-notifications-design.md` (commit 44c20f5) — CTO ruled all 8 open questions; rulings + terminology rule (DONE = «Hoàn tất») recorded in DECISIONS.md entry "N3 design approved". Task plan = report §F (P1 enum migration → P2 subscriptions table → P3 ticket dispatch → P4 household → P5 is_public+follow → P6 SLA scheduler + ContractExpiry marker fix → P7 FE resident → P8 FE admin bell → P9 docs).
- **N3 P1 DONE (2026-06-11):** V13 migration — `ALTER TYPE notification_type ADD VALUE` × 4 (`TICKET_CREATED`, `TICKET_SLA_WARNING`, `HOUSEHOLD_MEMBER_ADDED`, `TICKET_RATING_REQUESTED` per G7) + Java constants. Round-trip test `NotificationTypeRoundTripTest` (4 params, flush+clear+reload through NAMED_ENUM column). Commits: feat 0187644, test 15a365a. Suite 260/260 green.
- **N3 P2 DONE (2026-06-11):** V14 `notification_subscriptions` (UNIQUE user+entity, CHECK joined_via, 2 indexes, polymorphic entity_id no-FK by design) + backfill (creator column is `submitted_by_user_id`, NOT design's assumed `submitted_by`); entity `NotificationSubscription` + `SubscriptionJoinedVia` + repository (`existsBy…`, `deleteBy…`, ID-projection `findParticipantUserIds`, native `insertIfAbsent` ON CONFLICT DO NOTHING) + `SubscriptionService(Impl)` — subscribe idempotency via exists-check + conflict-ignoring insert (in-Java catch of unique violation would mark the surrounding TX rollback-only → DB-side ignore chosen). Tests `SubscriptionServiceTest` 6/6 (idempotent subscribe/unsubscribe, joinedVia-first-wins, participants exact, CHECK rejects invalid value). Suite 266/266. Backfill DB-verified: 228/228 CREATOR + 77/77 ASSIGNEE rows, 0 missing on pre-migration set (`reports/n3-p2-backfill-verify.md` — post-migration drift from committed test tickets is expected, P3 covers live creation). Commits: feat 07f0e93, test 0547103.
- **N3 P3 DONE (2026-06-11):** ticket lifecycle dispatch C1–C6 — C2 VN-localized (fix 1d74dd2, was live English); C1 create→active ADMINs minus actor (new `UserRepository.findActiveUserIdsByRole`), C3 NEW→ASSIGNED to thread snapshot (taken before assignee subscribes — assignee gets C2 only), C4 status change to thread minus actor (VN labels via new `TicketStatusLabels`, verbatim mirror of locked FE enumLabels — DONE=«Hoàn tất»), C5 DONE→submitter `TICKET_RATING_REQUESTED` (G7), C6 rate→assignee; live subscription writes (CREATOR on create, ASSIGNEE on assign, G4 old row kept on reassign); multi-recipient via batched saveAll+getReferenceById (announcement pattern), all in-mutation-TX. Tests `TicketDispatchTest` 8/8 (exact VN strings incl. literal «Hoàn tất», actor exclusion, recipient sets, G4 reassign + old assignee still gets C4, idempotent double-assign). Suite 274/274. Side-fix d90f98c: de-flaked `AmenityControllerTest.listBookings_adminSeesAllBookings` (unsorted page-100 lottery vs 209 accumulated dev-DB bookings → per-amenity filtered assertions); pre-existing parking phone-collision flake (phoneFromUid random 090-range vs committed users) noted, NOT fixed — rare, self-heals on rerun. Commits: fix 1d74dd2, feat 3a55192, test 1360d89, de-flake d90f98c.
- **N3 P4 DONE (2026-06-11):** C9 household notice in `createResident` — recipients `findActiveByApartmentId` minus new user minus actor (uniform exclusion), `HOUSEHOLD_MEMBER_ADDED`, VN «Thành viên mới» / «Cư dân {fullName} đã được thêm vào căn hộ {unit}.», ref Resident/{id} (FE NOTIF_ROUTES lacks the key → N1 unknown-type rule = mark-read only, no FE change), batched saveAll, empty-apartment no-op. Tests `ResidentHouseholdDispatchTest` 3/3 (active-members-only incl. moved-out exclusion + new-user-zero-rows; empty apartment; actor-in-household excluded). Suite 277/277. Commits: feat fe1fcbd, test d6b4f94.
- **N3 P5 DONE (2026-06-11):** V15 `tickets.is_public` (default FALSE, G3 immutable — no update path, rogue-JSON-field test proves ignore) + entity/DTOs (`Boolean isPublic` in request+responses, JSON key `isPublic`); `enforceReadAccess` RESIDENT allows public, but **`assertPresignAccess` split to its own strict `enforcePhotoAccess` (household/staff only) — presign deliberately NOT widened for public tickets pending F-05 (G8)**; redacted public view (detail `toRedactedDetail` + list `toRedactedSummary` — list redaction added beyond plan, else summary rows leak what detail hides): «Cư dân» placeholder, no submitter id/phone, block-only (no unitNumber/apartment id), photos empty, history timestamps+statuses only (no changedBy/notes), no assignee identities, no rating comment; `?visibility=mine|community` list filter — **default (null) = "mine" = pre-P5 scoping, existing FE unchanged**, community = `is_public=true` only, invalid → 400; follow/unfollow `POST|DELETE /api/tickets/{id}/follow` (RESIDENT, idempotent both ways, invisible private ticket → 404 no-existence-leak, FOLLOWER row joins P3 dispatch thread). Tests: `TicketPublicAccessTest` 10/10 (heart-pair: presign FORBIDDEN on public ticket for outsider + same caller reads redacted detail; field-level redaction; full view intact household+admin; scoping mine/community/invalid; admin list unredacted; follow idempotent + FOLLOWER row + private→404; follower receives C4 «Đã hủy») + `TicketControllerTest` rogue-isPublic test. Suite 288/288. Commits: feat 96ae285, feat 84fa619, test 695659d.
- **N3 P6 DONE (2026-06-12) — BE of N3 COMPLETE.** V16 sent-marker columns (`tickets.sla_warning_notified_at`/`sla_overdue_notified_at` + `contracts.expiry_notified_at` for G6); `TicketSlaScheduler` third job in scheduler/ (`0 */15 * * * *`, whole-run @Transactional): overdue scan FIRST (`sla_deadline < now`, marker null → C8 «Phản ánh quá hạn», BOTH markers set on already-overdue-at-first-sight per §D edge), warning scan lower-bounded `sla_deadline >= now` (excludes overdue; upper bound now+2h per G2) → C7 «Phản ánh sắp quá hạn» with deadline dd/MM HH:mm in Asia/Ho_Chi_Minh; recipients = assignee (if any) + active ADMINs deduped (G5, reuses `findActiveUserIdsByRole`), batched saveAll+getReferenceById. G6 fix separate commit: `findExpiringBetween` += `expiryNotifiedAt IS NULL`, marker set after successful insert in now-@Transactional run — once instead of daily×30. Tests: `TicketSlaSchedulerTest` 7/7 (second-run-zero both kinds, only-breach edge + both markers, DONE/CANCELLED + null-deadline exclusion, exact VN bodies incl. +07-vs-UTC cross-check via literal `ZoneOffset.ofHours(7)`, admin-assignee dedup) + `ContractExpiryOnceOnlyTest` 1/1 (bug-fix proof); existing Mockito `ContractExpirySchedulerTest` 4/4 untouched-pass. Suite 296/296. Commits: feat 63ff0a8, fix c276ca7, test fe68a39.
- **N3 P7 DONE (2026-06-12).** BE prerequisite (commit 32407d1): `TicketDetailResponse` viewer flags — `redacted` (primitive, set true ONLY in `toRedactedDetail`; false default on all other paths), `isFollowing` (`Boolean`, @Setter, set in `getTicketDetail` for RESIDENT callers only via new `SubscriptionService.isFollower` → derived `existsBy…AndJoinedVia(FOLLOWER)` — CREATOR/ASSIGNEE rows deliberately do NOT count; null for staff/mutation responses); 3 new tests in `TicketPublicAccessTest` (13/13; creator-row-not-following covered). FE (commit b4de552): resident `NOTIF_ROUTES` += `Ticket → /tickets/{id}` (route verified in App.tsx:48; HOUSEHOLD_MEMBER_ADDED/`Resident` type stays unmapped = mark-read only); create-form `isPublic` checkbox (default off, `tickets.publicToggle`); TicketsPage tabs «Của tôi»/«Cộng đồng» — community sends `visibility=community`, mine OMITS the param (= pre-P5 default, existing behavior untouched), page title 'Phản ánh của tôi'→'Phản ánh' (tabs carry the scoping now), map param renamed `t`→`tk` (i18n shadow), community rows render BE-redacted submitter «Cư dân» + block via optional chaining (NO FE hiding logic); detail page follow/unfollow button only when `redacted===true` (`useFollowTicket`/`useUnfollowTicket` POST/DELETE `/tickets/{id}/follow`, invalidate `['tickets', id]`, success toasts «Đã theo dõi/bỏ theo dõi phản ánh.»), `canRate` guarded `!redacted` (pre-existing hole: outsider on public DONE ticket saw the rate form BE would 403), history `changedBy` omitted gracefully. Types: partial `TicketDetailItem` (3 viewer flags typed + index signature). **Any-debt remaining:** resident ticket list rows + detail body still untyped (`tk: any`, index signature) — full TicketSummary/Detail FE typing deferred. Resident tsc + vite green; admin untouched (no shared pkg change).
- **N3 P8 DONE (2026-06-12) — N3 CODE COMPLETE (commit 25bb775).** Admin bell rows clickable: `useMarkNotificationRead` (identical resident pattern), `handleNotifClick` mark-read-then-navigate, admin `NOTIF_ROUTES` = `Ticket → /tickets/{id}` (route verified admin App.tsx:57) + `Contract → /reports` (expiring-contracts table = the CONTRACT_EXPIRING surface; ContractorsPage shows contractors only, no contract rows). `MaintenanceSchedule` (SCHEDULE_DUE) intentionally unmapped → mark-read only; Announcement/Resident types not admin-receivable (dispatch audiences are resident-scoped) → also unmapped. Admin-receivable type inventory: TICKET_CREATED Y, TICKET_SLA_WARNING Y, TICKET_SLA_BREACHED Y, TICKET_ASSIGNED Y (admin assignee), TICKET_STATUS_CHANGED Y (thread member), TICKET_RATED Y, CONTRACT_EXPIRING Y, SCHEDULE_DUE Y; ANNOUNCEMENT_PUBLISHED N, HOUSEHOLD_MEMBER_ADDED N, TICKET_RATING_REQUESTED N. Admin tsc + vite green; resident untouched.
- **✅ N3 COMPLETE END-TO-END (2026-06-12): P1–P8 code + CTO browser smoke-test PASSED (all 5 rounds, ports 80/81, incl. N1 resident bell) + P9 docs.** Suite: 296/296 full run at P6 + 3 P7 viewer-flag tests green in targeted run = 299 total. P9 (docs-only commit): API-SPEC — follow/unfollow endpoints, `visibility` param, `isPublic` on create, detail viewer flags + G8 redaction rule, PUT→POST fix (notifications read/read-all), spec:1069 divergence note, §12 notification event catalog (C1–C9 + SLA + schedulers + referenceType list). DECISIONS: household-shared ticket visibility ratified as intended. Backlog file: N3 flipped DONE; 4 new items (a)–(d) recorded — see `reports/module10-extended-backlog.md` «New backlog items».
- **Backlog (a)+(b) DONE (2026-06-12, commits 62239b6 + 639c98f), pending CTO quick browser check (admin ticket detail: submitter «{fullName} - {unitNumber}», assign-form date dd/MM; resident community view unchanged).** (a) admin detail was the ONLY full-view surface rendering the submitter. (b) was `datetime-local` (locale-driven + time component vs BE `LocalDate`) → existing `VNDatePicker`. Admin tsc+vite green; `TicketPublicAccessTest` 13/13 (redaction untouched); resident app untouched.
- **HARDENING SPRINT IN PROGRESS — H1 DONE (2026-06-12).** Design: `reports/hardening-design.md` (CTO approved §B; rulings E1/E3/E4/E5 + E2=Option 1 in DECISIONS.md "Hardening sprint rulings"). H1 shipped: presign expiry 1h→10min (0c72583, suite 299/299, heart-pair 13/13), F-05 → RESOLVED in `reports/security-remediation.html`, API-SPEC §13 file-surface access matrix + R-4 notes on the unimplemented contract-attachment endpoints. **N2/N4's F-05 gate is now LIFTED for announcement images (per matrix); public-ticket photos stay blocked permanently (E4).**
- **H2 DONE (2026-06-12) — N2 UNBLOCKED.** `assertPresignAccess` prefix dispatch: `tickets/` unchanged (DB row + `enforcePhotoAccess`), `announcements/` any-authenticated (E3, no DB-row check by design until N2's table — comment in code), other prefixes 403 deny-by-default. Pre-flight: `ticket_photos` 0 rows in dev DB, sole key generator emits `tickets/` since inception — no legacy shapes. Tests: `PresignPrefixRoutingTest` 4/4 (incl. unauthenticated 401 via MockMvc + no-residency resident pass) + `TicketPublicAccessTest` 13/13 regression. Suite **303/303**. Commits: feat 6f3dd96, test adc3b15. Spec matrix row flipped to implemented-read-path; N2 gate-lifted note in extended backlog.
- **H3 DONE (2026-06-12) — BE httpOnly refresh cookie, DUAL-MODE.** Cookie on login/refresh (HttpOnly, SameSite=Strict, Path=/api/auth — verified actual mapping, context-path `/`; Max-Age from `jwt.refresh-token-expiry-ms`), Secure via `app.auth.cookie-secure` (`AUTH_COOKIE_SECURE`, default false — http dev/demo lockout trap documented in application.yml). Refresh: body-first (legacy unchanged) else cookie+`X-Requested-With` (403 without); shared validation + SEC-05 both paths; body validation moved to controller (body now optional, same VALIDATION_ERROR). Logout clears cookie; revocation untouched. CORS: origins were already exact + allowCredentials — only `X-Requested-With` added to allowedHeaders. Login body STILL returns refreshToken (dual-mode window — removal at close-out post-H5, DECISIONS note). Tests: `AuthCookieTest` 6 + `AuthCookieSecureFlagTest` 2 (Secure asserted in BOTH states; cookie-path rate limit 3→429 with XFF-isolated IPs) + `AuthControllerTest` 6 regression. Suite **311/311**. Commits: feat 0090241, test f215c49.
- **H4 DONE (2026-06-12) — FE both apps cookie-based session.** All `gemek_refresh` localStorage reads/writes deleted (authStore login/logout/bootstrap/refreshToken + 401 interceptor, both apps); `withCredentials: true` on both apiClient instances + raw-axios refresh calls; refresh = POST /auth/refresh empty body + `X-Requested-With: XMLHttpRequest` (BE cookie path, 403 without); login ignores body refreshToken (dual-mode window); one-time `localStorage.removeItem('gemek_refresh')` in bootstrap (F-04 legacy cleanup). Reload bootstrap reworked: no localStorage gate — always attempts cookie refresh; 401 → unauthenticated → login screen (App.tsx authStatus tri-state untouched, no redirect loops, interceptor skip-list covers /auth/refresh). BE untouched. Builds green both apps (tsc + vite). Commits: resident 1e8ce69, admin d4e931c. ⚠ Dev cookie collision: cookies host-scoped not port-scoped — admin+resident on localhost:80/:81 in ONE browser overwrite each other's refresh cookie; H5 testing MUST use two browser profiles (DECISIONS.md H4 entry).
- **H5 SMOKE DONE (2026-06-15→16) — points 1–5 PASS; point 6 FIXED + re-verified, sprint closed out (see below).** Point 6 revealed a silent identity switch: on the shared host-scoped cookie, the admin tab adopted the resident session (and vice-versa) because neither app validated the authenticated user's ROLE. Fix (FE only, BE untouched): client-side role-gate in both authStores, two places each — (1) bootstrap, after cookie-refresh+`/auth/me`, and (2) post-login, on the login-response user. Allowed sets from ground truth: admin = `['ADMIN','BOARD_MEMBER']` (admin FE `RequireRole` literals; zero TECHNICIAN refs → technicians do NOT use this portal), resident = `['RESIDENT']`. Mismatch → LOCAL-only state reset (`accessToken/user` null, `unauthenticated`); **never `/auth/logout`** — that revokes the user's refresh tokens and would kill their legitimate session in the other portal/tab. Post-login mismatch additionally throws an Error carrying error code `WRONG_PORTAL` → existing `getVnErrorMessage` maps it to «Tài khoản không có quyền truy cập cổng này.» (new shared key in `@gemek/ui` errorMessages, no hardcoded component string). Builds green both apps (tsc + vite). Commits: resident a2521e4, admin fe22555. **CTO re-check point 6:** same browser, login admin :80 → login resident :81 → return to admin tab → expected: kicked to LOGIN (not resident identity); resident tab keeps working.
- **✅ HARDENING SPRINT H1–H5 COMPLETE + CLOSED OUT (2026-06-16).** Close-out shipped cookie-only refresh: H3 dual-mode body channel removed — `/auth/login` + `/auth/refresh` no longer return `refreshToken` in the JSON body (`@JsonIgnore` on `LoginResponse.refreshToken`, kept only to build the cookie), and `/auth/refresh` takes the httpOnly cookie as the SOLE source (no cookie → 401; cookie without `X-Requested-With` → 403). FE login-comment cleanup (body no longer carries the token). **All security findings closed: F-04 ≡ SEC-20 unified FIXED (httpOnly cookie), F-05 RESOLVED (H1/H2)** — both `SECURITY_AUDIT_PROGRESS.md` (20/20 FIXED, 0 NOT-FIXED) and `reports/security-remediation.html` reconciled. Topology ruling ratified (DECISIONS close-out 2026-06-16): two-portal simultaneous use is DESIRED, enabled in prod by SEPARATE SUBDOMAINS (independent cookie jars); NEVER a shared cookie `Domain`; prod requires `cookie-secure=true`; the FE role-gate is prod-valid defense-in-depth. Full suite **311/311** green. Commits: feat ba4c0b7, test 879ffaf.
- **⚠ TECHNICIAN locked out of all portals (surfaced in close-out)** — admin gate = `[ADMIN,BOARD_MEMBER]`, resident = `[RESIDENT]`; TECHNICIAN (valid BE role, many `@PreAuthorize` refs) cannot log into any portal. Fail-safe/intended for now; **the staff user-mgmt design (c) MUST resolve where technicians work** — recorded in `reports/module10-extended-backlog.md` item (c).
- **⏸ BACKLOG (c) staff user-mgmt — IN PROGRESS. P0 DONE (CTO rulings, commit 20ed802); P1 DONE (2026-06-17).** Rulings: `DECISIONS.md` "Backlog (c) … CTO rulings (2026-06-17)". Evidence: `reports/c-staff-usermgmt-investigation.md`. P1 notes: `reports/c-p1-userspage.md`. Phase plan (order FIXED): P0 docs → **P1 UsersPage (done)** → P2 RequireRole audit on ALL admin pages → P3 admit TECHNICIAN (tickets-only) → P4 audit_logs [split] → P5 docs.
  - **P1 landed (FRONTEND ONLY, admin):** new admin `UsersPage.tsx` over EXISTING `/api/users` (list+filters / create / edit / soft-deactivate / reset-password); typed `StaffUserItem`. Hooks added in admin `api/hooks.ts` (`useCreateUser/useUpdateUser/useDeactivateUser/useResetUserPassword` + `del` helper). Route `/users` (`RequireRole ['ADMIN']`) + nav «Tài khoản» (ADMIN-only) + `nav.users` i18n. **ADMIN-role guardrail:** explicit confirm dialog on create-ADMIN or edit-promotion-to-ADMIN. Self-row deactivate hidden (read-only `useAuthStore` id; BE `SELF_OPERATION_NOT_ALLOWED` guard intact).
  - **Shared map:** added `UserRole` group to `@gemek/ui` enumLabels (ADMIN/TECHNICIAN/RESIDENT/BOARD_MEMBER → VN) per locked enum-map convention — separate `feat(ui)` commit. **No new errorMessages key needed** (PHONE/EMAIL_ALREADY_EXISTS, SELF_OPERATION_NOT_ALLOWED, VALIDATION_ERROR, PASSWORD_POLICY_VIOLATION already mapped).
  - **NOT TOUCHED (P3 boundary):** authStore / ALLOWED_ROLES / role-gate / other pages' RequireRole — unchanged.
  - **Verified:** ui 66/66; admin tsc + vite build green. **NOT browser-verified — CTO smoke step (port 80, `docker compose up -d --build nginx`).**
  - **P1b — smoke-test defects fixed (2026-06-17, FE-only).** Diagnosis (HTTP+DB proof): `reports/c-p1b-diagnosis.md`. Root cause of D1+D2 was a single FE key mismatch: `GET /api/users` serializes the flag as **`isActive`** (`UserResponse @JsonProperty`), but P1 `StaffUserItem`/reads used `active` (→ undefined). **D1** (edit isActive not persist): BE PUT verified correct (HTTP 200, DB `is_active` flips); FE `openEdit`/list read wrong key → fixed `active`→`isActive`. **D2** (status filter wrong): BE filter param `isActive` verified correct (returns only matching rows); the displayed-wrong was the same key mismatch → same fix; no param change. **D3** (reset-password): **NOT-A-DEFECT** — HTTP 204, DB `password_hash` changed, login with new password returned a token; no code change (dev account pw restored to `Demo@1234`). Technician-login-blocked is EXPECTED (role-gate, P3 not done). Guardrail + self-row deactivate-hidden preserved. admin tsc+vite green. Commit: fix(admin) below. **NOT browser-verified after fix — CTO re-smoke on :80.**
  - **P2.5 — ticket-stats block on TicketsPage (2026-06-17, FE-only; CTO-approved scope addition).** Goal: technician sees ticket STATISTICS on the Tickets page, NOT the business dashboard (dashboard stays `[ADMIN,BOARD_MEMBER]`, P2 Option 2). **STEP A data-source diagnosis** (`reports/c-p2.5-ticketstats-source.md`, commit 3a2cdc4) concluded **(i)** — technician-safe source EXISTS: the ticket LIST endpoint `GET /api/tickets` (`@PreAuthorize` admits TECHNICIAN) returns `PageResponse.total` = accurate whole-dataset count for the caller's BE-scoped view (`buildScopeSpec`: TECHNICIAN = assigned-to-me OR status=NEW; ADMIN/BOARD = all). Dashboard aggregate `/api/reports/dashboard` is `[ADMIN,BOARD_MEMBER]`-gated AND bundles contracts/occupancy → unusable. **STEP B (FE-only):** `useTicketCount(filter)` hook (`get('/tickets',{...filter,size:1}).then(r=>r.total)`); `TicketStats` block at top of TicketsPage — 4 status count cards (NEW/ASSIGNED/IN_PROGRESS/DONE, dashboard StatCard styling) + by-category panel (`labelFor` titles, `t('dashboard.ticketsByCategory')` header). **SLA-breached/overdue OMITTED** (no overdue filter on list; SLA endpoints ADMIN/BOARD-gated → not technician-derivable; omitted, NOT fabricated — surfacing it later needs a BE change, separate gated decision). No role-branching (ticket data, visible to all roles reaching the page). NOT touched: routing/landing/RequireRole/authStore/BE. **Accuracy:** card value = `PageResponse.total` = JPA `count()` over the same predicate as a DB GROUP BY (ADMIN scope unrestricted) → exact by construction; DB ground truth confirmed NEW=325/ASSIGNED=73/IN_PROGRESS=57/DONE=68, categories MAINTENANCE_REPAIR=170/COMPLAINT=286/ADMINISTRATIVE=74/SUGGESTION_FEEDBACK=1/OTHER=0(no row→card 0). **Verified:** admin tsc(exit 0) + vite build green. **Code-review:** no critical bug; 2 notes (object queryKey; `as number` on untyped `get`) both match the existing `useTickets` convention → record-defer. **NOT browser-verified — CTO smoke step (port 80).** Commit: feat(admin) below.
  - **P2.6 — `overdue` filter on GET /api/tickets (2026-06-17, BACKEND ONLY; TDD).** Authoritative report: `reports/c-p2.6-overdue-filter.md`. Closes the SLA-breached gap P2.5 had to omit + the §2/§5 finding of `reports/c-reports-investigation.md` (BE change needed for technician SLA regardless of Cách 1/2 → chose Cách 2 + the minimal filter). **Added optional `Boolean overdue` to the LIST endpoint** (already admits TECHNICIAN, already role-scoped): `TicketController.listTickets` + `TicketService`/`TicketServiceImpl.listTickets` + `buildFilterSpec`. **`@PreAuthorize` UNCHANGED, `buildScopeSpec` UNCHANGED** — filter addition, not a permission change; exposes no data outside the caller's scope. **Predicate `overdue=true`** = `sla_deadline IS NOT NULL AND sla_deadline < now AND status NOT IN (DONE,CANCELLED)`, **mirrored exactly** from `TicketRepository` aggregates (`:43/91/130/159`) + `findSlaOverdueCandidates` (`:184-186`) → count matches Reports `slaBreached`/dashboard `overdueRequests`. **`overdue=false`** = logical complement via `cb.not(breached)` (NULL-deadline/future/closed); **`null`/absent = no filtering, behavior unchanged**. **Null-safety:** Criteria API `cb.isNotNull`+`cb.lessThan` (never JPQL nullable param, Hibernate 6) → NULL deadline never matches `true`. **Role-scope preserved** (ANDed on top; proved by `overdueTrue_respectsTechnicianRoleScope`). **Tests (TDD, RED→GREEN)** in `TicketLifecycleIntegrationTest`: true-only-breached (DONE/NULL/future excluded, total==1), absent-returns-all (regression, total==4), technician-scope. RED proof: pre-change `expected <1> but was <4>`. **Suite 314/314, BUILD SUCCESS.** **HTTP+DB cross-check:** dev-DB canonical = **459 overdue-open / 603 total**; live :80 (OLD image) `?overdue=true`→603 == no-filter (confirms old code ignores param); new-code HTTP path proven by integration test; **post-deploy live ADMIN `?overdue=true` must return total=459** — literal live-459 deferred to the gated docker redeploy (running container is old image). Commits: `test(ticket)` + `feat(ticket)` (separated; feat-first to keep every commit green, RED proven in tree). **FE consumption = P2.7** (SLA card via `useTicketCount({overdue:true})` + `?overdue=true` drill-down) — NOT started.
  - **P2.7 — SLA-breached card + stat-card drill-down on TicketsPage (2026-06-17, FRONTEND ONLY).** Authoritative report: `reports/c-p2.7-ticketstats-fe.md`. One file: `frontend/apps/admin/src/pages/TicketsPage.tsx`. **PART 1 — SLA card:** new `SlaCountCard` via `useTicketCount({overdue:true})` (same `PageResponse.total` mechanism as the P2.5 status cards; P2.6 filter). VN label `t('dashboard.slaBreached')`=«Trễ hạn» (reused, not coined). Role-correct server-side, no FE branching. No fabrication: `isError→'—'`, `isLoading→'…'`, else `data??0`. Status grid `grid-cols-4→5`. **PART 2 — drill-down (all roles):** every card/row clickable → filtered list on same page. Mapping: NEW/ASSIGNED/IN_PROGRESS/DONE→`status=`, each category→`category=`, SLA→`overdue=true`. **URL-param seeding (both directions):** `useSearchParams` is the **single source of truth** — `category/status/overdue/page` derived from URL (no local useState for them); click sets URL, mount/param-change seeds filter → landing `/tickets?overdue=true` or `?status=NEW` applies immediately. **Sync:** dropdowns→`setFilter` (merge, preserve others, reset page); stat cards→`drillDown` (REPLACE all filters with the one → list `total`==card number); pagination→`goPage`. `apartmentId`/`showCreate`/`formError` stay local (create-modal state). **overdue-no-control handling:** no dropdown for overdue; when active (drill-down/direct URL) the list query honors it regardless of visible controls + a clearable red chip «Trễ hạn ✕» (`setFilter({overdue:''})`) renders in the filter bar — visible + reversible, not silent. **Verified:** admin tsc(exit 0) + vite build green (587 modules). **/code-review (cavecrew):** 0 real bugs; 3 findings all record-defer (page `||0` flagged redundant but is REQUIRED — `Math.max(0,NaN)=NaN`; `setFilter('false')` round-trip has no live caller; mixed bool/string params serialized fine by axios per existing `useTicketCount`). NOT touched: BE/@PreAuthorize/authStore/role-gate/routing. **NOT browser-verified — CTO :80 smoke step.** Commits: feat(admin) + docs(context) below.
  - **⚠ CTO :80 SMOKE (REQUIRED — running container is the OLD image, pre-P2.6):** P2.6's `overdue` BE filter + P2.7's bundle are NOT in the running stack. Smoke: (1) `docker compose up -d --build backend` (REBUILD — pulls the P2.6 filter into the jar); (2) `docker compose up -d --build nginx` (new admin bundle); (3) ADMIN on :80 → TicketsPage SLA card reads **459** (P2.6 dev-DB ground truth, high due to known dev-DB pollution, not a bug); (4) click SLA card → `/tickets?overdue=true`, list `total`==459, red «Trễ hạn ✕» chip clearable; (5) spot-check NEW card → `/tickets?status=NEW`, `total`==card number.
  - **Resume pointer:** P1 + P1b + P2.5 + **P2.6 (BE overdue filter) + P2.7 (FE SLA card + drill-down)** complete. **Awaiting CTO :80 smoke** (rebuild backend+nginx per the checklist above; ADMIN SLA card=459; drill-down lands filtered). **NEXT after smoke = P2** (audit & tighten `RequireRole` on ALL admin pages so a technician reaches no non-ticket page; dashboard ruling = Option 2 per `reports/c-p2-route-audit.md`) **→ then P3** (admit TECHNICIAN, tickets-only). Order FIXED: **Do NOT start P3 until P2 lands.** P4 (audit_logs) + P5 (docs) follow.
  - **✅ P2 STEP B SHIPPED (LATE) 2026-06-17 — route-guard audit: `reports/c-p2-stepB-applied.md`.** Was DESIGNED in STEP A (`c-p2-route-audit.md` §4) but never shipped — P2.5/2.6/2.7 ticket-stats work jumped ahead; the P3 blocker re-surfaced it. **FRONTEND ONLY, no role admitted (P3 still forbidden), no BE change, H5 invariants untouched.** Applied: (1) new `src/lib/homePathFor.ts` (TECHNICIAN→`/tickets`, else `/dashboard`); (2) `App.tsx` — `/dashboard` guarded `[ADMIN,BOARD_MEMBER]` (Option 2), `/tickets`+`/tickets/:id` guarded `[ADMIN,BOARD_MEMBER,TECHNICIAN]` (route-level pre-position only; authStore gate stays `[ADMIN,BOARD_MEMBER]`), `RequireRole` fallback + new `HomeRedirect` (index, `*`, deferred `/amenities`+`/parking`) all role-aware via `homePathFor`; (3) `Layout.tsx` nav — dropped TECHNICIAN from dashboard entry → **technician sees ONLY Tickets**; (4) `LoginPage.tsx` — both `/dashboard` navigate literals → `homePathFor`; (5) in-page admin-only controls hidden from TECHNICIAN per BE `@PreAuthorize`: new-ticket button (create=ADMIN+RESIDENT) + Assign "Phân công" card (assign=ADMIN-only); status-update KEPT (status=ADMIN+TECHNICIAN, core work). **P2.5/2.7 stat block + drill-down + «Trễ hạn ✕» chip LEFT INTACT** (role-neutral; URL-param seeding untouched; build-traced). **Redirect-loop trace OK** (every role's landing route admits it; no role sent to a rejecting route; `homePathFor(undefined)` unreachable in authenticated tree). **Pre-existing OUT-OF-SCOPE flag:** BOARD_MEMBER still sees create/assign/status controls the BE 403s for them (predates STEP B, unrelated to technician scope, "ADMIN/BOARD unchanged" → left as-is; recommend separate ruling). admin tsc(exit 0)+vite(588 modules) green. /code-review (cavecrew): clean, 0 real bugs. **NOT browser-verified — static audit (technician cannot log in until P3).** Commits: feat(admin) + docs(context) below.
  - **(c) ORDER NOW RECONCILED: P1✓ P1b✓ P2.5✓ P2.6✓ P2.7✓ P2-STEP-B✓ → P3 UNBLOCKED.** Then P4 (audit_logs, split) + P5 (docs).
  - **✅ P3 SHIPPED 2026-06-17 — TECHNICIAN admitted to admin portal: `reports/c-p3-admit-technician.md`.** FRONTEND ONLY, ONE constant. **P2 preconditions verified present BEFORE the change** (ruling 7): `/dashboard`=[ADMIN,BOARD_MEMBER] (App.tsx:65), `/tickets`+`/tickets/:id`=[ADMIN,BOARD_MEMBER,TECHNICIAN] (:69,:70), `homePathFor` backs fallback+index+`*`+amenities+parking, nav TECHNICIAN on /tickets only — tree was guarded, so widening is safe. **Change:** admin `authStore.ts:32` `ALLOWED_ROLES` → `['ADMIN','BOARD_MEMBER','TECHNICIAN']`. Single constant feeds **BOTH** gates — bootstrap (`:65`, after cookie-refresh+`/auth/me`) AND post-login (`:82`, on login-response user) — so both admit TECHNICIAN atomically. **H5 invariants preserved EXACTLY:** mismatch → LOCAL state reset only (`:66`,`:83`), **NEVER `/auth/logout`**; post-login mismatch still throws `WRONG_PORTAL` (`:84-86`) → `getVnErrorMessage` «Tài khoản không có quyền truy cập cổng này.» **Resident app UNTOUCHED** — `resident/authStore.ts:32` still `['RESIDENT']`; ADMIN/BOARD_MEMBER/TECHNICIAN all rejected from resident (reset-not-logout). No RequireRole/nav/homePathFor/page touched. admin tsc(exit 0)+vite(588 modules) green. /code-review (cavecrew): clean, 0 issues — both gates use the constant, reset-not-logout + WRONG_PORTAL intact, resident unaffected. **(c) feature work complete pending P4 (audit_logs, split) + P5 (docs).** **NOT browser-verified — CTO :80 smoke (first live technician login):** Commits: feat(admin) + docs(context) below.
    - **⚠ CTO :80 SMOKE (REQUIRED — rebuild, not restart, to pick up FE change):** (1) `docker compose up -d --build backend nginx`. (2) Technician account logs into admin :80 → SUCCESS, lands `/tickets`. (3) Technician sees ONLY the Tickets nav item; stat cards show role-scoped counts (SLA = their overdue count, NOT 459); no new-ticket / Assign buttons. (4) Technician manually visits `/residents`,`/users`,`/reports`,`/dashboard` → each bounces to `/tickets`, no loop, no blank. (5) Technician CANNOT log into resident :81 (rejected). (6) RESIDENT still cannot log into admin :80 (rejected; LOCAL reset, not server logout). (7) ADMIN/BOARD_MEMBER unchanged: full nav, dashboard reachable.
  - **✅ (c) follow-up SHIPPED 2026-06-17 — admin create-ticket entry point REMOVED (all roles): `reports/c-remove-admin-createticket.md`.** Admins only PROCESS tickets; residents create via the resident app. **UI-only, intentional removal — NOT a permission change. BE `POST /api/tickets` untouched (resident app still uses it); resident app untouched.** Entry form was a **button + inline modal** on `TicketsPage.tsx` (NO `/tickets/new` route — nothing to redirect/close). Removed: new-ticket button, create modal+form, `handleCreate`, `loadApartmentOptions`, `showCreate`/`apartmentId`/`formError` state, the **redundant P2 STEP B `isTechnician` create-guard** (dead once button gone), now-unused imports, and the admin-only `useCreateTicket` hook in `api/hooks.ts` (sole consumer was TicketsPage → fully removed). **KEPT:** all ticket PROCESSING (status-update + Assign forms on TicketDetailPage, the P2.5/2.7 stat cards + drill-down + «Trễ hạn ✕» chip, list/filters/pagination/row-navigate); `TicketDetailPage`'s `isTechnician` Assign-guard (unrelated to create); i18n keys `tickets.new/create/creating/modalTitle` (left as harmless dead keys). No create affordance (button OR URL) for ANY of ADMIN/BOARD_MEMBER/TECHNICIAN. admin tsc(exit 0)+vite(588 modules, 433KB, down from 437) green. Residual-symbol grep → none. /code-review: cavecrew agent aborted on session cost guard → record-defer; correctness covered by tsc+vite+grep. **NOT browser-verified — CTO :80 smoke (rebuild nginx).** Commits: feat(admin) + docs(context) below.
  - **P2.8 — server-derived `mine` filter on GET /api/tickets (2026-06-17, BACKEND ONLY; TDD). Authoritative report: `reports/c-p2.8-mine-filter.md`.** Backs the next FE card «Phân công cho tôi» (assigned-to-me) for ALL admin roles. Mirrors the P2.6 `overdue` pattern exactly. **Added optional `Boolean mine`** to the LIST endpoint (already admits all roles, already role-scoped): `TicketController.listTickets` + `TicketService`/`TicketServiceImpl.listTickets` + `buildFilterSpec` (now also receives `principalId`). **`@PreAuthorize` UNCHANGED, `buildScopeSpec` UNCHANGED** — filter on top of scope, not a permission change. **Server-derived, IDOR-safe:** the assignee target is `principalId` (the same caller id `buildScopeSpec` uses) — FE passes NO user id, nothing to forge; deliberately avoids the IDOR an `assigneeId=<id>` param would create. **Predicate `mine=true`** = `cb.and(isNotNull(assignedToUser.id), equal(assignedToUser.id, principalId))` (Criteria API). **Null-safety:** `isNotNull` guard ⇒ an UNASSIGNED ticket (assignee NULL) can never match `mine=true` (also INNER-join drops it; explicit guard documents intent). **`mine=false` semantics = NO-OP** (no assignee filtering, identical to absent) — only `mine=true` is active; "not assigned to me" is not a product need, YAGNI. **Intentionally diverges from P2.6's `overdue=false` complement** (logged in DECISIONS.md). **Scope per role:** ADMIN/BOARD → all-tickets-assigned-to-me; TECHNICIAN → `(assigned-to-me OR NEW) AND mine` collapses to assigned-to-me (**strict subset**, NEW arm drops, no bypass); RESIDENT → effectively empty. **`mine`×`overdue` AND independently.** **Tests (TDD, RED→GREEN)** in `TicketLifecycleIntegrationTest` (+`UserRepository` autowire): `mineTrue_returnsOnlyTicketsAssignedToCaller` (other-user + unassigned excluded, total==1), `mineAbsent_returnsAll_unchangedBehavior` (regression), `mineFalse_isNoOp_returnsAll`, `mineTrue_respectsTechnicianRoleScope` (NEW + other-tech drop out), `mineTrueOverdueTrue_returnsCallerOwnOverdueOpen`. `TicketPublicAccessTest` got the forced 2-call-site `+ null` signature adaptation. RED proof: pre-change `expected <1> but was <3>`. **Suite 319/319, BUILD SUCCESS** (was 314). **HTTP+DB cross-check:** dev-DB canonical admin-assigned = **23 / 705 total**; live :80 (OLD image) `?mine=true`→705 == no-filter (confirms old code ignores param); new-code HTTP path proven by integration tests; **post-deploy live ADMIN `?mine=true` must return total=23** — literal live-23 deferred to the gated docker redeploy (running container is old image). Commits: `feat(ticket)` (feat-first to keep every commit green, RED proven in tree) + `test(ticket)` + `docs(context)`. **FE card phase = NEXT** («Phân công cho tôi» StatCard via `useTicketCount({mine:true})` + `/tickets?mine=true` drill-down + new «Phân công cho tôi ✕» clearable chip) — NOT started, awaiting CTO go.
  - **✅ P2.8 FE — «Phân công cho tôi» assigned-to-me stat card (2026-06-17, FRONTEND ONLY). Authoritative report: `reports/c-mine-card-fe.md`.** Completes the (c) ticket-stats follow-ups (create-ticket removal done; assigned-to-me card done). Two files: `TicketsPage.tsx` + i18n `vi.ts` (`dashboard.assignedToMe='Phân công cho tôi'`, CTO-confirmed wording). **No BE/@PreAuthorize/authStore/route-guard touch.** **Card:** new `MineCountCard` via `useTicketCount({mine:true})` → `GET /api/tickets?mine=true` `PageResponse.total` — SAME mechanism as the P2.5 status cards / P2.7 SLA card. Added to stat grid (`grid-cols-5`→`6`), indigo, `'—'`-on-error (no fabrication). **Visible to ALL admin roles, NO FE role-branching** — `mine` is server-derived so each caller's card shows THEIR own assigned count (admin→admin's, technician→technician's). **Drill-down:** click → `drillDown({mine:'true'})` REPLACES filters → `/tickets?mine=true`; existing `useSearchParams` seeding (`mine = get('mine')==='true'`) + list param `...(mine && {mine:true})` filters the list on landing; list `total` == card count by construction. **Chip:** `mine` has no dropdown (like `overdue`) — honored from URL regardless of controls + clearable indigo «Phân công cho tôi ✕» chip via `setFilter({mine:''})` (clears only mine, preserves others). **mine×overdue coexist:** both spread independently into list params (`...(overdue&&{overdue:true}), ...(mine&&{mine:true})`), BE ANDs (P2.8 `mineTrueOverdueTrue` test); both chips render + clear independently; only `drillDown` replaces-all (so a card count == its single-filter list). **Verified:** admin tsc(exit 0) + vite(588 modules, 433.93 kB) green. **/code-review (cavecrew):** clean, 0 correctness bugs. **NOT browser-verified — CTO :80 smoke (rebuild backend+nginx; running container is OLD image w/o P2.8 BE filter, so card reads 0 until redeploy).** **Smoke:** after `docker compose up -d --build backend nginx`: (1) admin (P2.8-tested account) → card reads **23** (dev-DB ground truth for THAT user; technician sees own count, not 23); (2) click card → `/tickets?mine=true`, list `total`==23, indigo chip visible; (3) chip clears it; (4) combine with overdue → both chips, list=caller's own overdue-open, clear-one-keeps-other; (5) all P2.5/2.7 cards + «Trễ hạn ✕» chip unchanged. Commits: feat(admin) + docs(context) below.
  - **⛔ P3 ATTEMPTED 2026-06-17 → HALTED (BLOCKER), now RESOLVED by P2 STEP B above: `reports/c-p3-BLOCKER-p2-gap.md`.** A P3 session brief assumed P2 route-guards were DONE (it conflated **P2.7 ticket-stats card** with **P2 route-guard audit**). Ground truth this session: **P2 STEP B never shipped.** `c-p2-route-audit.md` is STEP A only ("No code edited yet"); `App.tsx` still has `/dashboard` (`:54`), `/tickets` (`:58`), `/tickets/:id` (`:59`) **UNGUARDED**; `RequireRole` fallback + index + catch-all all → unguarded `/dashboard`; **`homePathFor` does NOT exist** (grep NONE); DashboardPage still leaks contracts+occupancy. Per ruling 7 widening the gate before per-page RequireRole is **FORBIDDEN** (live leak: TECHNICIAN would land on unguarded `/dashboard`). The P3 one-line edit (`ALLOWED_ROLES`+=TECHNICIAN) was applied, tsc+vite verified green, then **REVERTED** — tree clean, nothing shipped. **P3 BLOCKED until P2 STEP B lands.** CTO must: (1) confirm dashboard/landing ruling (Option 2 recommended), (2) approve P2 STEP B implementation. Then P3 = the same one-liner.
- **NEXT (hardening done): (c) staff user-mgmt — P1 DONE, see above; next P2 after CTO smoke. Then (d) move-out admin UI, N2 (unblocked), N4, TEMP_HIDDEN_DEFERRED, VN user guide.
- ⚠ **TECH-DEBT (own session, not mixed into N3): shared dev-DB test pollution** — part of the suite writes committed rows to the Docker dev DB (249 garbage tickets, 209 bookings observed 2026-06-11; caused the P2 backfill-gap misreading, the amenity list flake (de-flaked d90f98c), and the parking phone-collision flake (still latent)). Fix direction: migrate Docker-required tests to testcontainers or per-run schema reset.
- ⚠ Multiple residencies: impossible — `uq_residents_active_user` partial unique index (V4:22).

**NEXT — remaining major items:**
1. ~~Date-format mm/dd→dd/mm~~ DONE 2026-06-11: formatVNDate/formatVNDateTime in @gemek/ui (commit b1db38b, ui 58/58 green) + 18 display spots wired (resident 9 = 195ff8e, admin 9 = 75f5c87); both builds green. Timezone decision (local-time render, intended) + KIND-B native-input limitation recorded in DECISIONS.md 2026-06-11. KIND-C wire ISO untouched. Inventory: reports/date-format-diagnosis.md. NOT browser-verified — CTO step (ports 80/81).
2. TEMP_HIDDEN_DEFERRED removal (hidden nav/features).
3. Module 10 notification dispatch — IN PROGRESS, see section above.
4. Vietnamese user guide.
5. ~~Hardening sprint: F-04, F-05, SEC-20.~~ ✅ COMPLETE 2026-06-16 (H1–H5 + close-out; all findings FIXED — see hardening section above).

**Resident cluster 1 COMPLETE (2026-06-10):**
- viShared empty-state refined: `common.emptyYet` / `common.emptyFound` replace `common.empty`; 11 ui tests green. Commit 24aff81.
- Translated: MyTicketsPage ('Phản ánh của tôi', '+ Tạo mới', emptyYet, 'Tạo phản ánh đầu tiên', modal 'Tạo phản ánh'), MyBookingsPage ('Lượt đặt của tôi', emptyYet), TicketDetailPage (back/labels/Photos→Hình ảnh/Timeline→Lịch sử/'Khởi tạo' fallback/rating block), ProfilePage ('Trang cá nhân', 'Vai trò:', 'Đăng nhập gần nhất:', 'Đổi mật khẩu' form, 'Đăng xuất'). nav.tickets + home.activeTickets switched 'Yêu cầu'→'Phản ánh'. Commit cbd99c0.
- Verified: 11/11 ui tests + `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step.
- Flagged, NOT changed (already-VN, old "yêu cầu" wording): MyTicketsPage 'Gửi yêu cầu' + 'Loại yêu cầu', TicketDetailPage error 'Không thể tải yêu cầu hỗ trợ.' — needs CTO terminology-sweep decision.

**NEXT: resident cluster 2 — AnnouncementsPage, AmenitiesPage (TEMP_HIDDEN_DEFERRED), ParkingPage (TEMP_HIDDEN_DEFERRED). Then enum display-maps (separate step), then admin app.**

**Step 1 pilot COMPLETE (2026-06-10):**
- `packages/ui/src/lib/vi.ts` — `viShared` dict (Hủy/Lưu/Sửa/Đang tải.../Trước/Sau/Thao tác/Trạng thái + `common.empty` = 'Không có {item}') + `createT(...dicts)` factory + `interpolate()`; exported from `packages/ui/src/index.ts`. 10 unit tests green (`vi.test.ts`). Commit 39dc7a9.
- `frontend/apps/resident/src/i18n/vi.ts` — resident dict (nav/layout/home) + app-bound `t = createT(vi, viShared)` (app dict shadows shared). `src/i18n/enums.ts` NOT created — enum display-maps are a separate later step.
- Resident `Layout.tsx` + `HomePage.tsx` translated via `t()`. Key terms: Home→Trang chủ, Tickets→Yêu cầu, Vehicles→Phương tiện, News→Tin tức, Profile→Cá nhân, 'Hello, {name}'→'Xin chào, {name}' (interpolated). Commit 66b2515.
- Verified: `tsc --noEmit` + `vite build` green (resident). NOT browser-verified — CTO step (`docker compose up -d --build nginx`).
- Untouched (per scope): getVnErrorMessage / meta.successMessage feedback, enum `value=` attrs, all other files.

**⏸ NEXT: STOPPED for CTO pattern review. Do NOT roll out further until CTO approves the pilot pattern.**

**Rollout order (after pilot approval):**
- Resident remainder (ParkingPage, ProfilePage, TicketDetailPage, AmenitiesPage, etc.)
- Enum display-maps — separate step (`src/i18n/enums.ts` per app)
- Admin app — ~3–4 clusters per reports/i18n-inventory.md (ParkingPage, AmenitiesPage, ReportsPage, then remaining pages)

---

## Current State
- **Phase:** DONE (all gates and phone-as-login migration)
- **Gate:** G1 ✅ G2 ✅ G3 ✅ G4 ✅ (2026-06-03)
- **Last completed:** 2026-06-08 — Dup-phone 500 → 409 fix: GlobalExceptionHandler now maps DataIntegrityViolationException → 409 CONFLICT (defense-in-depth); backend Docker rebuilt to deploy step-5 existsByPhone guard in ResidentServiceImpl; ResidentsPage 409 inline message uses server message instead of hardcoded wrong string. 14 tests green. Commits: b13807d (fix) + 2971559 (test).
- **Previously last completed:** 2026-06-08 — Demo seed script (`scripts/seed-demo-local.sql`): 3 blocks, 10 apartments, 30 residents, 5 staff (2 ADMIN + 3 TECHNICIAN). Password `Demo@1234` (BCrypt-12). Run: `cat scripts/seed-demo-local.sql | docker exec -i gemek-postgres psql -U gemek -d gemek`. Verified: counts match, 0 dup phones, 0 multi-active-residents.
- **Previously last completed:** 2026-06-05 — POST /api/residents: transactional user+resident create in one call. userId removed (breaking). New fields: fullName/email/password/phone/dateOfBirth + resident fields. email-duplicate → 409, apt-not-found → 404, both roll back (no orphan user). 184/184 tests compile; 183 pass (1 pre-existing Block sort flakiness, unrelated). Commits: 60f008f (tests) + 4216970 (feat). Backend rebuilt.
- **Previously last completed:** 2026-06-05 — Central toast system: Toaster + toast() in @gemek/ui, wired into TanStack MutationCache (both portals). Success toast default "Thao tác thành công", error maps 401/403/5xx to Vietnamese, passes serverMsg for 4xx. skipErrorToast on 12 admin + 5 resident mutations (all with inline catch). skipSuccessToast on MarkAllRead (both), MarkAnnouncementRead, CreateBooking (inline success UX), PublishAnnouncement (compound action). nginx rebuilt.
- **Previously last completed:** 2026-06-05 — ParkingPage assign form: vehicleId + apartmentId raw UUID inputs → async SearchableSelect dropdowns. Apartment first, vehicle filters by selected apartmentId (GET /api/vehicles?apartmentId=&search=&size=10&isActive=true) — prevents vehicle/apartment mismatch. parkingSlotId still derived from clicked slot row (unchanged). Feature remains TEMP_HIDDEN_DEFERRED. 201 confirmed via API. GET /api/vehicles `search` param added (Criteria API, OR licensePlate/brand/model, null-safe); 9/9 tests pass.
- **Also 2026-06-05:** Ticket assign form: replaced raw UUID input with async SearchableSelect dropdowns. Staff: 3-call merge (ADMIN+BOARD_MEMBER+TECHNICIAN) — BE only supports single role param. Contractor: shown only for MAINTENANCE_REPAIR, hidden otherwise. Mutual exclusivity enforced. scheduledDate + notes added to payload. Admin: VehiclesPage with async resident SearchableSelect (GET /api/residents?search=&size=20&isActive=true), apartment auto-derived from selected resident (no independent apartment picker), 409→"Biển số đã được đăng ký". Resident: MyVehiclesPage self-scoped via /residents/me (no list calls to /residents or /apartments), unit shown read-only. nginx rebuilt; 201 and 409 verified via curl.
- **Note:** AdminSeeder is idempotent by design — changing ADMIN_PASSWORD in .env after the admin exists requires scripts/reset-admin-password.sql (or docker compose down -v) to update the stored BCrypt hash.
- **Blocked:** None

---

## Completed Modules
| Module | Phase | Tests | Committed |
|--------|-------|-------|-----------|
| System Architecture v2 | architect | N/A | Yes |
| DB Schema v2 | architect | N/A | Yes |
| API Spec v2 | architect | N/A | Yes |
| Auth + RBAC | backend | 3 tests | Yes (bb08316) |
| Apartments & Blocks | backend | 4 tests | Yes (c4b6f00) |
| Residents & Vehicles | backend | 5 tests (Docker req.) | Yes (424df57) |
| Ticket Management | backend | 8 tests | Yes (5dcb446) |
| Contractors & Contracts | backend | 5 tests | Yes (ce782f9) |
| Announcements | backend | 4 tests | Yes (53cae29) |
| Amenity Booking | backend | 5 tests | Yes (8863449) |
| Parking | backend | 4 tests | Yes (dc19526) |
| Notifications + Audit Log | backend | 3 tests | Yes (50e07a5) |
| Admin Portal (12 pages) | frontend | N/A | Yes (ba1c634) |
| Resident Portal (9 pages) | frontend | N/A | Yes (ba1c634) |
| Shared UI Package | frontend | N/A | Yes (ba1c634) |

---

## Approved Gates
| Gate | Approved | CTO Notes |
|------|---------|-----------|
| G1 — Techstack | ✅ 2026-05-29 | |
| G2 — Backend | ✅ 2026-05-29 | |
| G3 — Frontend | ✅ 2026-05-29 | SAST backend+frontend both PASS WITH NOTES before approval |
| G4 — Testing | ✅ 2026-06-03 | 149/149 tests pass, security audit 19/20 fixed, SEC-20 deferred, app boots fresh DB |

---

## Backend Module Queue
| # | Module | Status | Committed |
|---|--------|--------|-----------|
| 0 | Project scaffold (pom.xml, docker-compose, Flyway base) | ✅ done | Yes |
| 1 | Auth + RBAC | ✅ done | Yes |
| 2 | Apartments & Blocks | ✅ done | Yes |
| 3 | Residents & Vehicles | ✅ done | Yes |
| 4 | Ticket Management | ✅ done | Yes |
| 5 | Contractors & Contracts | ✅ done | Yes |
| 6 | Announcements | ✅ done | Yes |
| 7 | Amenity Booking | ✅ done | Yes |
| 8 | Parking | ✅ done | Yes |
| 9 | Reports & Dashboard | ✅ done | Yes |
| 10 | Notifications + Audit Log | ✅ done | Yes |

---

## Session Resume Instructions
If context is lost, read these files in order:
1. `PROGRESS.md` (this file) — current state
2. `DECISIONS.md` — all decisions made
3. `docs/ARCHITECTURE.md` — system design
4. `docs/API-SPEC.md` — API contracts
5. `docs/DB-SCHEMA.sql` — database schema
6. Then continue from "Current State" above
