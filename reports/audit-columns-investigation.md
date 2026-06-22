# Audit Columns Investigation — Spring Data JPA Auditing Adoption + AuditLogAspect Removal

**Type:** READ-ONLY investigation + design. No code changed, no aspect removed.
**Date:** 2026-06-18
**Branch / HEAD:** `deploy/local` @ `3972303`
**Tree state:** clean for code (only untracked `reports/*` + `scripts/GenHash.java`; zero tracked modifications).

---

## CTO rulings recorded (from prior reports)

- User entity has **no** `created_by`/`updated_by` today — only `created_at`/`updated_at` (`reports/p4-createdby-coverage-check.md`).
- Contract + Announcement **do** carry creator attribution — this report determines *how* (below).
- `AuditLogAspect` writes rows for 4 User actions (CREATE / UPDATE / DELETE(deactivate) / RESET_PASSWORD). **CTO ruling: remove the aspect** once `created_by`/`updated_by` lands. Reset-password attribution and full before/after change-history are **knowingly given up** (accepted trade-off, not a regression).

---

## §1 — Entity inventory (23 `@Entity` classes)

Legend: ✔ present · — absent · n/a not applicable (no update path).

| # | Entity | Table | created_at | updated_at | created_by | updated_by | Shape |
|---|--------|-------|:--:|:--:|:--:|:--:|------|
| 1 | `User` | users | ✔ | ✔ | — | — | mutable |
| 2 | `Block` | blocks | ✔ | ✔ | — | — | mutable |
| 3 | `Apartment` | apartments | ✔ | ✔ | — | — | mutable |
| 4 | `Resident` | residents | ✔ | ✔ | — | — | mutable |
| 5 | `Vehicle` | vehicles | ✔ | ✔ | — | — | mutable |
| 6 | `Contractor` | contractors | ✔ | ✔ | — | — | mutable |
| 7 | `Contract` | contracts | ✔ | ✔ | ✔ FK | — | mutable (has creator, **no updater**) |
| 8 | `MaintenanceSchedule` | maintenance_schedules | ✔ | ✔ | — | — | mutable |
| 9 | `Ticket` | tickets | ✔ | ✔ | — | — | mutable |
| 10 | `Amenity` | amenities | ✔ | ✔ | — | — | mutable |
| 11 | `AmenityBooking` | amenity_bookings | ✔ | ✔ | — | — | mutable |
| 12 | `ParkingSlot` | parking_slots | ✔ | ✔ | — | — | mutable |
| 13 | `ParkingAssignment` | parking_assignments | ✔ | ✔ | — | — | mutable |
| 14 | `Announcement` | announcements | ✔ | ✔ | ✔ FK | — | mutable (has creator, **no updater**) |
| 15 | `ResidentHistory` | resident_history | ✔ | — | — | n/a | append-only |
| 16 | `ContractPayment` | contract_payments | ✔ | — | — | n/a | append-only |
| 17 | `GuestVehicle` | guest_vehicles | ✔ | — | — | n/a | append-only |
| 18 | `Notification` | notifications | ✔ | — | — | n/a | append-only |
| 19 | `NotificationSubscription` | notification_subscriptions | ✔ | — | — | n/a | append-only |
| 20 | `TicketStatusHistory` | ticket_status_history | `changed_at` | — | — | n/a | event log (actor intrinsic) |
| 21 | `TicketPhoto` | ticket_photos | `uploaded_at` | — | — | n/a | event log (uploader intrinsic) |
| 22 | `AnnouncementRead` | announcement_reads | `read_at` | — | — | n/a | junction (reader intrinsic) |
| 23 | `AuditLog` | audit_logs | ✔ | — | — | n/a | being decommissioned-write |

Cited: timestamp/FK grep across `module/**/entity/*.java`; `Contract.java:105-106`, `Announcement.java:109-110`, `AuditLog.java:42`, history/junction time columns `TicketStatusHistory.java:82`, `TicketPhoto.java:85`, `AnnouncementRead.java:56`.

**Migration surface (the real number):**
- **21 of 23** entities lack `created_by` (only Contract + Announcement have it).
- **23 of 23** entities lack `updated_by` (nobody has it).
- Recommended actor-column targets:
  - **12 mutable tables get BOTH** `created_by` + `updated_by`: users, blocks, apartments, residents, vehicles, contractors, maintenance_schedules, tickets, amenities, amenity_bookings, parking_slots, parking_assignments.
  - **2 tables get `updated_by` only** (already have `created_by`): contracts, announcements.
  - **5 append-only tables get `created_by` only** (no update path → no `updated_by`): resident_history, contract_payments, guest_vehicles, notifications, notification_subscriptions.
  - **4 excluded**: ticket_status_history, ticket_photos, announcement_reads (actor is already the row's own user FK), and audit_logs (being retired-write).

---

## §2 — How Contract / Announcement set `created_by` today → **MANUAL**

Not Spring Data. There is **no** `@CreatedBy`/`@LastModifiedBy`/`AuditingEntityListener`/`@EnableJpaAuditing` anywhere in the codebase (grep across all `*.java` returned only `UserServiceImpl` (for `@Auditable`) and `Auditable.java` (the annotation def) — i.e. zero Spring Data auditing).

- `Contract.createdBy` is a `@ManyToOne User` on `created_by_user_id` (`Contract.java:105-106`), set explicitly: `ContractorServiceImpl.java:259` → `contract.setCreatedBy(creator)`, where `creator = userRepository.findById(principalId)` (`:244`).
- `Announcement.createdBy` is a `@ManyToOne User` (`Announcement.java:109-110`), set explicitly: `AnnouncementServiceImpl.java:179` → `announcement.setCreatedBy(creator)`, `creator` from `principalId` (`:167`). Also set in the second creation path `:521` (`.createdBy(creatorRef)`).
- `principalId` flows in as a **method parameter** from the controller: `@AuthenticationPrincipal UserPrincipal principal` → `principal.getId()` (e.g. `ContractorController.java:106-108`).
- Timestamps everywhere are **manual `@PrePersist`/`@PreUpdate`** lifecycle callbacks (e.g. `Contract.java:125-139`), **not** `@CreatedDate`/`@LastModifiedDate`.

**Consequence for adoption:**
- `@EnableJpaAuditing` **must be added** (does not exist).
- Contract/Announcement must **migrate onto the listener and stop manually calling `setCreatedBy(...)`** — otherwise they double-write (manual + auditing). Since both currently model the field as `@ManyToOne User`, convergence requires a field-type decision (see §4 + design).
- Timestamps can stay manual (out of scope), OR optionally fold into auditing later — **not** part of this effort to avoid churn.

---

## §3 — `AuditorAware` source (who is the current actor)

Canonical pattern already exists in the aspect — replicate it in an `AuditorAware`:

`AuditLogAspect.java:117-127` reads:
```
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth == null || !auth.isAuthenticated()) return null;     // system/scheduler
Object principal = auth.getPrincipal();
if (principal instanceof UserPrincipal) return ((UserPrincipal) principal).getId();  // UUID
return null;
```
`UserPrincipal.getId()` returns the user `UUID` (`UserPrincipal.java:63-65`) — the **same id** controllers pass as `principalId`.

**Proposed `AuditorAware<UUID>`:** return `Optional.ofNullable(...)` of the principal UUID; return **`Optional.empty()`** when there is no authenticated `UserPrincipal` (scheduler / seed / Flyway / login flow). Empty → Spring Data leaves the actor column `null`, no NPE. This matches the aspect's `null`-on-unauthenticated behavior and the Hibernate null-safety learnings (no eager deref of a possibly-absent principal).

> Note: an `AuditorAware<User>` variant would force a `userRepository.findById` on every save (the aspect does this at `:85`). `AuditorAware<UUID>` avoids the DB hit because `UserPrincipal` already holds the id. **Recommend `<UUID>`.**

---

## §4 — Column type + nullability

- **Type:** `created_by` / `updated_by` as plain **`UUID` columns** (`@CreatedBy private UUID createdBy`), matching the `AuditorAware<UUID>` actor. Backing `uuid` SQL column.
- **DB FK:** keep a **real FK to `users(id) ON DELETE SET NULL`** — this is the *existing precedent* (`V6:22` contracts creator, `V7:29` announcements creator, `V10:47` audit_logs user all use `ON DELETE SET NULL`). Best of both: integrity at the DB layer + cheap UUID actor in the app layer (no entity load on write). User deletion stays safe (actor goes null, row survives).
  - *Trade-off:* a hard FK slightly complicates hard-deletes of users — but the system already soft-deactivates users and every existing actor FK is `SET NULL`, so this is consistent, not new risk. The looser "plain UUID, no FK" option is simpler but loses referential integrity and diverges from existing audit columns — **not recommended**.
- **Nullability:** `created_by` **nullable**. System/seed/scheduler/migration writes have no authenticated actor → must accept `null` (per §3, §6). `updated_by` nullable for the same reason.

---

## §5 — Aspect removal blast radius (entire surface mapped)

`common.audit` is referenced by **exactly 5 files** (grep `AuditLog*|common.audit` across all `*.java`):
1. `common/audit/Auditable.java` — the annotation (`@interface`, `:25`).
2. `common/audit/AuditLogAspect.java` — the `@Aspect @Component` (`:35-37`).
3. `common/audit/entity/AuditLog.java` — the entity (`audit_logs`).
4. `common/audit/repository/AuditLogRepository.java` — `JpaRepository`, **no custom queries, no other caller**.
5. `module/user/UserServiceImpl.java` — the **only** consumer: 4 `@Auditable` usages at `:97` (CREATE), `:142` (UPDATE), `:167` (DELETE), `:188` (RESET_PASSWORD).

**Hidden deps: NONE.** `AuditLogRepository` is injected only by the aspect. `AuditLog` entity is used only by aspect + its repo. The aspect's `SecurityContext` read (`:117-127`) is self-contained — nothing else relies on the aspect running. Removing the aspect + 4 `@Auditable` annotations is the complete change.

**Recommendation on the table/entity: KEEP, stop-writing — do NOT drop.**
- Removing the aspect stops all writes to `audit_logs` (the only writer). That alone satisfies the ruling.
- **Dropping the table is destructive and irreversible** and would discard historical audit rows. Keep `audit_logs` table + `AuditLog` entity + `AuditLogRepository` as-is (now write-idle). A later, deliberate `Vxx` migration can drop it if the CTO truly wants the history gone. State explicitly: this report does **not** drop it.
- `@Auditable.java` annotation: remove after its 4 usages are removed (no other usages), OR keep as a harmless no-op; **recommend remove** for cleanliness since nothing reads it once the aspect is gone.

---

## §6 — Backfill

Existing rows predate the new columns. New columns are **nullable** → existing rows get **`NULL` actor**. This is **acceptable**: historical authorship is genuinely unknown, and we will not invent it. **No backfill attempted.** Same applies to `audit_logs`: existing rows are kept untouched (read-only history).

---

## DESIGN OUTPUT

### A. Flyway migration — `V17__add_audit_columns.sql` (next free version; current max V16)

One migration adding actor columns + FKs (`ON DELETE SET NULL`), **without** touching existing `created_at`/`updated_at`:

- **BOTH columns** (`created_by uuid`, `updated_by uuid`) on 12 tables:
  `users, blocks, apartments, residents, vehicles, contractors, maintenance_schedules, tickets, amenities, amenity_bookings, parking_slots, parking_assignments`.
- **`updated_by uuid` only** on: `contracts, announcements` (they already have `created_by_user_id`).
  - ⚠ Naming inconsistency to resolve: existing creator columns are `created_by_user_id`; new sweep uses `created_by`. Decide one convention. **Recommend** the new tables use `created_by`/`updated_by`; for contracts/announcements add `updated_by` and *optionally* rename `created_by_user_id`→`created_by` in the same migration for uniformity (rename touches the two entities' `@JoinColumn`/field — see §B convergence).
- **`created_by uuid` only** on 5 append-only tables:
  `resident_history, contract_payments, guest_vehicles, notifications, notification_subscriptions`.
- Each new column: `... uuid` + `ADD CONSTRAINT fk_<table>_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL` (and same for `updated_by`).
- **Excluded:** ticket_status_history, ticket_photos, announcement_reads, audit_logs.

### B. JPA plan

1. **`@EnableJpaAuditing(auditorAwareRef = "auditorAware")`** on a `@Configuration` (new — none exists).
2. **`AuditorAware<UUID>` bean** reading `SecurityContextHolder` exactly like `AuditLogAspect:117-127`; `Optional.empty()` when no `UserPrincipal`.
3. **Base `@MappedSuperclass`** (e.g. `AuditableEntity`) with `@EntityListeners(AuditingEntityListener.class)`, `@CreatedBy UUID createdBy`, `@LastModifiedBy UUID updatedBy`. Mutable entities extend it; append-only entities use a `created_by`-only variant (or `@EntityListeners` + `@CreatedBy` field directly).
   - Keep existing manual `@PrePersist`/`@PreUpdate` timestamps as-is (don't migrate timestamps now — avoid churn).
4. **Contract / Announcement convergence (the one real decision):** they currently model `createdBy` as `@ManyToOne User` set manually.
   - **Option B1 (recommended):** change field to `@CreatedBy private UUID createdBy`, delete the manual `setCreatedBy(...)` calls (`ContractorServiceImpl:259`, `AnnouncementServiceImpl:179,521`), add `@LastModifiedBy UUID updatedBy`. Cost: the response mappers that expose creator info (`ContractResponse`, announcement responses) must resolve the name from the UUID instead of the `User` association — a contained mapper/DTO touch. Eliminates double-write, fully unifies.
   - **Option B2:** keep `@ManyToOne User createdBy` and use `AuditorAware<User>` (loads user per save). Lower DTO churn, but a DB hit per write and a `User`-typed base class that the plain-UUID tables can't share cleanly. **Not recommended.**
   - This is a contained decision (recommend B1); flag for CTO confirm at AUD.2 rather than a hard blocker.

### C. Aspect removal plan

- Remove `AuditLogAspect.java`.
- Remove the 4 `@Auditable` annotations in `UserServiceImpl` (`:97,:142,:167,:188`) and the import.
- Remove `Auditable.java` (no remaining usages).
- **Keep** `audit_logs` table, `AuditLog` entity, `AuditLogRepository` (write-idle; preserves history). No DROP migration.
- Net behavior: reset-password attribution + before/after change-history are no longer captured — **intentional** per CTO ruling.

### D. DECISIONS entry to record (at impl time, not now)

> Adopt Spring Data JPA auditing system-wide (`@EnableJpaAuditing` + `AuditorAware<UUID>` + `@CreatedBy`/`@LastModifiedBy` on a base `@MappedSuperclass`); actor stored as `UUID` with a real `users(id) ON DELETE SET NULL` FK (matches existing creator-column precedent). Contract/Announcement converge off manual `setCreatedBy` onto the listener (Option B1). `AuditLogAspect` + `@Auditable` removed; `audit_logs` table/entity **kept write-idle** (drop deferred). Reset-password attribution + full change-history **intentionally no longer audited** — knowing trade-off, not a regression.

### E. Proposed phase breakdown

- **AUD.1** — `V17` migration (actor columns + FKs) + `@EnableJpaAuditing` config + `AuditorAware<UUID>` + base `@MappedSuperclass`; wire the 12 "both" tables + 5 append-only "created_by-only" tables; tests (auditing sets actor on persist/update; null actor for unauthenticated/seed).
- **AUD.2** — Converge `contracts` + `announcements` (add `updated_by`, optional column rename, switch to `@CreatedBy`/`@LastModifiedBy`, delete manual `setCreatedBy`, fix response mappers). Confirm no double-write.
- **AUD.3** — Remove `AuditLogAspect` + `@Auditable` (4 usages + annotation file); keep `audit_logs` table write-idle; docs/DECISIONS + `docs/API-SPEC.md` if any response shape changes (creator field).
