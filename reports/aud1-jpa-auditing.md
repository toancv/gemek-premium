# AUD.1 — Spring Data JPA Auditing Foundation

**Date:** 2026-06-18 · **Branch:** `deploy/local` · **Plan:** `reports/audit-columns-investigation.md`
**Scope:** V17 actor-column migration + `@EnableJpaAuditing` + `AuditorAware<UUID>` + base `@MappedSuperclass` + entity wiring. TDD. No Contract/Announcement convergence (AUD.2). No aspect removal (AUD.3).

---

## Migration — `V17__add_audit_columns.sql` (next free; V16 was max)

Applied cleanly to dev Postgres: Flyway `Successfully applied 1 migration ... now at version v17`.

**17 tables migrated.** All new columns `uuid`, nullable, with FK `users(id) ON DELETE SET NULL`
(`fk_<table>_created_by` / `fk_<table>_updated_by`), matching the existing creator-column precedent.

- **12 mutable — BOTH `created_by` + `updated_by`:** users, blocks, apartments, residents, vehicles,
  contractors, maintenance_schedules, tickets, amenities, amenity_bookings, parking_slots, parking_assignments.
- **5 append-only — `created_by` ONLY (no update path):** resident_history, contract_payments,
  guest_vehicles, notifications, notification_subscriptions.
- **NOT touched:** contracts, announcements (AUD.2 adds their `updated_by`; `created_by_user_id` kept,
  no rename per CTO ruling) + 4 excluded (ticket_status_history, ticket_photos, announcement_reads, audit_logs).
- Prod note recorded in-file: large prod tables may prefer FK `NOT VALID` + later `VALIDATE`; dev is fine in-line.

No backfill — historical authorship is genuinely unknown (nullable columns absorb existing rows).

## JPA wiring

- `config/JpaAuditingConfig` — `@Configuration @EnableJpaAuditing(auditorAwareRef = "auditorAware")` (none existed before).
- `common/audit/SecurityAuditorAware` — `@Component("auditorAware") implements AuditorAware<UUID>`.
  Mirrors `AuditLogAspect.resolveActorId()` (`:117-127`): reads `SecurityContextHolder`; returns the
  `UserPrincipal` UUID; returns **`Optional.empty()`** when auth is null/unauthenticated or principal is
  not a `UserPrincipal`. No `userRepository` load (uses `UserPrincipal.getId()`).
- `common/audit/AuditableEntity` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`) —
  `@CreatedBy UUID createdBy` (`created_by`, `updatable=false`) + `@LastModifiedBy UUID updatedBy` (`updated_by`).
- `common/audit/CreatableEntity` — same, `@CreatedBy` only (append-only variant, no `updatedBy`).
- 12 mutable entities `extends AuditableEntity`; 5 append-only `extends CreatableEntity` (17 total).
- **Timestamps untouched:** every entity keeps its manual `@PrePersist`/`@PreUpdate` `created_at`/`updated_at`.
  Base classes add ONLY actor fields. JPA spec runs both the entity-listener and entity-method callbacks.

## Confirmations

- **AuditorAware empty-on-unauthenticated:** confirmed by test — persist with cleared `SecurityContext`
  leaves `created_by`/`updated_by` NULL, **no NPE**. Spring Data `touchAuditor` early-returns on empty
  auditor, so the login `last_login_at` save (no principal yet) does NOT null an existing `updated_by`.
- **Contract / Announcement untouched** (AUD.2) — verified absent from V17 and unmodified entities.
- **AuditLogAspect untouched** (AUD.3) — not modified.

## Tests (TDD) — `AuditingActorCaptureIntegrationTest`, 4/4 green

1. Mutable entity: persist while authenticated → `createdBy == updatedBy ==` principal; update under a
   second actor → `createdBy` unchanged, `updatedBy ==` second actor (createdBy immutable).
2. Null-actor: cleared context → `createdBy`/`updatedBy` NULL, no exception.
3. Append-only: Notification gets `createdBy` on persist; reflection asserts no `updatedBy` field on
   `Notification` / `CreatableEntity`; `AuditableEntity` does carry it.
4. Regression: manual `created_at`/`updated_at` still populated.

`@Transactional` (rolled back) + unique-phone-per-row → robust against shared-dev-DB pollution.

## Verification

- New test class: **4/4 PASS**.
- Full suite: **331 tests — 189 pass, 142 fail, 0 errors.** All 142 failures are the pre-existing
  `login → 401` from the corrupt admin BCrypt hash in the shared dev DB
  (`reports/ADMIN-LOGIN-DIAGNOSIS.md`, env/docker-compose `$` interpolation). **Proven pre-existing:**
  reverting the 17 entity changes to HEAD and running `UserControllerTest` still fails 4/4 with the same
  401. Zero non-401 failures — no entity/auditing regression introduced by AUD.1.
- `/code-review`: Java correctness `[]`, migration `[]`. Three test-clarity nits raised; two fixed
  (assertions no longer wrapped in `catchThrowable`/`assertThatCode`); one **deferred** (unique-phone
  collision on shared DB — LOW risk, `@Transactional` rollback + 13-digit unique source).

## API-SPEC

No endpoint added/removed/changed in AUD.1 — actor columns are internal persistence only, not yet exposed
in any response. `docs/API-SPEC.md` needs no change here (creator/modifier response fields are an AUD.2 concern).
