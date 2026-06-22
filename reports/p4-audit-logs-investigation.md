# P4 — Audit Logs Persistence: Investigation & Design

**Mode:** READ-ONLY. No code modified. Design-first; implementation deferred pending CTO rulings.
**Branch:** `deploy/local` @ `75eef1a` — tree clean (only untracked `reports/`, `scripts/GenHash.java`).
**Date:** 2026-06-18

---

## ⚠️ Premise correction

The task brief assumes `AuditLogAspect` is a **DEBUG-stub that persists nothing**. **That is no longer true.**
The aspect already **persists real `AuditLog` rows** to the DB. What it does **not** do is capture
`old_value` / `new_value` (before/after) or `ip_address`. So P4 is **not** "replace a stub with DB writes" —
it is **"add before/after value capture + IP + an audit read path to an already-persisting aspect."**

---

## §1 — The aspect (`common/audit/AuditLogAspect.java`)

- **Pointcut:** `@Around("@annotation(auditable)")` — intercepts every method annotated `@Auditable`
  (`AuditLogAspect.java:71`).
- **What it does today (NOT a stub):**
  1. `log.debug(...)` the intercept (`:73`).
  2. `joinPoint.proceed()` runs the business method **first** (`:77`).
  3. In a **fire-and-forget try/catch** (`:80–105`): resolves actor UUID from `SecurityContextHolder`,
     loads the `User` (`orElse(null)`), builds an `AuditLog`, sets `user`/`action`/`entityType`/`entityId`,
     and calls `auditLogRepository.save(...)` (`:98`). On exception → `log.warn`, never rethrows (`:101–104`).
- **What it sets:** `user` (FK), `action`, `entityType`, `entityId` (via reflection `getId()` on the return
  value, `resolveEntityId`, `:138–151`).
- **What it does NOT set:** `oldValue`, `newValue`, `ipAddress`. ← **the P4 gap.**
- **Data available at interception:** method args (`joinPoint.getArgs()` — currently unused),
  return value (`result`), principal (`SecurityContextHolder`). It does **NOT** capture before-state
  because it only acts **after** `proceed()`.

## §2 — What is audited today (the persistence surface)

Exactly **4 sites**, all in `module/user/UserServiceImpl.java`, all entityType `"User"`:

| Action | Method | Line | Before/after meaning |
|--------|--------|------|----------------------|
| `CREATE` | `createUser` | `:97` | before = null; after = new user |
| `UPDATE` | `updateUser` | `:142` | before = pre-edit user; after = edited user |
| `DELETE` | `deactivateUser` | `:167` | soft-delete: before `active=true` → after `active=false` |
| `RESET_PASSWORD` | `resetPassword` | `:188` | **sensitive** — value must NOT be recorded |

No contract / apartment / role-change / move-out sites are annotated yet. Role change is logged at WARN
inside `updateUser` (`:146–152`) but is **not** a separate audit action. P4 scope = these 4; broadening
the `@Auditable` surface to other domains is a separate decision.

## §3 — Existing model + schema (all already built)

- **Entity:** `common/audit/entity/AuditLog.java` — fields: `id` (UUID gen), `user` (`@ManyToOne` LAZY,
  nullable), `action`, `entityType`, `entityId`, `oldValue` (`@JdbcTypeCode(JSON)` jsonb), `newValue`
  (jsonb), `ipAddress` (len 45), `createdAt` (`@PrePersist` UTC). **The before/after columns already exist.**
- **Repository:** `AuditLogRepository extends JpaRepository<AuditLog, UUID>` — CRUD only, **no query methods**.
- **Migration:** `V10__create_notifications_audit.sql:36–52` already creates `audit_logs`
  (`old_value JSONB`, `new_value JSONB`, `ip_address`, `created_at`) + FK `ON DELETE SET NULL` +
  indexes on `user_id`, `(entity_type, entity_id)`, `created_at`.

**Conclusion:** schema/entity/repo/table **exist and are complete**. P4 needs **no schema creation** —
only (optionally) an `actor_role` column (§8) and a read path (§6). The work is in the **aspect** and a
**read endpoint**, not the DB.

## §4 — before/after capture feasibility (the hard question)

The aspect runs its save **after** `proceed()`. By then UPDATE/DELETE have already mutated and saved the
entity, so the **pre-mutation state is gone** from the aspect's view (it sees only args + the after-result).

| Action | Before capturable as-is? | Notes |
|--------|--------------------------|-------|
| CREATE | ✅ yes | before = null; after = result (`UserResponse`) |
| UPDATE | ❌ no — needs pre-load | aspect must extract the `id` arg and **load the entity before `proceed()`** to snapshot before-state; current after-proceed design can't |
| DELETE (deactivate) | ❌ no — needs pre-load | same: snapshot `active=true` before `proceed()` |
| RESET_PASSWORD | n/a (excluded) | value never recorded (§5) |

**So before-state = PARTIAL.** CREATE is trivial; UPDATE/DELETE require restructuring the aspect to
pre-load by the `id` argument **before** `proceed()`. Generic reflection over arbitrary method signatures
(which arg is the id? which is the entity?) is the fragile part — this drives the §5 / aspect-design ruling.

---

# Design questions — **CTO RULING NEEDED**

## §5 — Sensitive-field exclusion (MANDATORY) — **CTO RULING NEEDED**

`passwordHash` (and any secret/token/hash) MUST NEVER land in `old_value`/`new_value`. The aspect must
**not** blind-serialize whole entities.

**Options:**
- **A. Field denylist constant** in the aspect (skip field names matching `password|secret|token|hash`).
  Simple, central, but name-based and easy to miss a new sensitive field.
- **B. `@AuditIgnore` field annotation** on entity fields. Explicit, self-documenting, but requires
  annotating every sensitive field and trusting authors.
- **C. Per-action opt-out** — add `boolean captureValues() default true` to `@Auditable`; sensitive
  actions set `captureValues=false` (action recorded, no value at all).

**Recommendation: B + C combined, never serialize the raw entity.** Serialize an explicit field map / a
dedicated snapshot DTO, with `@AuditIgnore` (B) as a hard denylist applied during serialization, **and**
`captureValues=false` (C) on `RESET_PASSWORD` so that site records the action with **null** old/new.
Defense in depth: even if someone forgets `@AuditIgnore`, RESET_PASSWORD never serializes values at all.

## §6 — PII handling & who may READ audit logs — **CTO RULING NEEDED**

Once before/after holds old+new email/phone, `audit_logs` becomes a **PII store**. Today there is **no
read path** (no controller, repository has no finders). Proposal:

- Add **ADMIN-only** read endpoint, e.g. `GET /api/v1/audit-logs` (paged; filter by user, entityType,
  entityId, date range), gated `@PreAuthorize("hasRole('ADMIN')")`.
- **Ruling needed:** (a) build the viewer in P4 or defer? (b) ADMIN-only confirmed, or a narrower
  audit/compliance role? **Recommendation:** build a minimal ADMIN-only read endpoint in P4.3 (it is the
  point of persisting before/after); defer any export/UI.
- API-SPEC note: no endpoint added yet → no `docs/API-SPEC.md` change in this investigation. The read
  endpoint, once ruled in, ships with its API-SPEC update in the same phase.

## §7 — Transaction boundary (CRITICAL) — **CTO RULING NEEDED**

Current aspect saves **after** `proceed()` inside a try/catch. **Risk:** the business method is
`@Transactional`; if the audit `save()` joins that same transaction, a swallowed `PersistenceException`
can still mark the tx **rollback-only**, breaking the business op despite the catch. Ordering of the
`@Transactional` advice vs this custom aspect is also not explicitly set (`@Order` absent on both).

**Options:**
- **A. Same transaction** — audit failure can roll back the business action. Undesirable.
- **B. `REQUIRES_NEW`** on the audit write — independent tx; failure isolated. Records intended state
  even if business tx later rolls back (minor over-record risk).
- **C. After-commit hook** (`@TransactionalEventListener(AFTER_COMMIT)` or
  `TransactionSynchronization`) — audit only **committed** changes; cleanest correctness.

**Recommendation: C (after-commit), or B as the simpler fallback.** After-commit guarantees audit reflects
only what actually committed and can never roll back the business op. Set explicit `@Order` so the audit
advice sits outside the transactional advice.
**Failed attempts:** current code audits **success only** (`proceed()` throws → save skipped). Recommend
keeping success-only for P4; a separate `*_FAILED` action can be added later if compliance wants attempts.

## §8 — Schema shape — **CTO RULING NEEDED (small)**

Existing columns already satisfy before/after: `id, user_id (actor), action, entity_type, entity_id,
old_value (jsonb), new_value (jsonb), ip_address, created_at`. before/after as **JSONB** (already chosen) —
good for flexible diffs.

- **Gap vs the task's ideal shape:** no `actor_role`. The actor's role is reachable via the `user` FK, but
  the user's role can **change later**, so the FK gives the *current* role, not the role *at action time*.
- **Recommendation:** add an immutable **`actor_role` snapshot** column (small `Vxx` migration) so the
  audit row is self-contained and tamper-evident. Null-safe (nullable, like `user_id`).

## §9 — Retention — **CTO RULING NEEDED (direction now, build later)**

Full before/after at ~1000 units grows fast. The `created_at` index already supports time-based pruning.

**Direction options:** (A) time-based purge job keeping N months; (B) monthly **range partitioning** on
`created_at` (drop old partitions cheaply); (C) keep-all + cold archive.
**Recommendation:** rule a **retention window now** (suggest **24 months**) so the design accommodates it;
implement via a scheduled purge job (simplest) or partitioning (scales better). Build can defer to P4.4 —
only the **window decision** is needed now.

---

## Proposed P4 phase breakdown (confirm after rulings)

| Phase | Scope | Status |
|-------|-------|--------|
| **P4.1** | Schema/entity | **Mostly DONE.** Only optional `actor_role` column (§8) pending ruling. |
| **P4.2** | Aspect value capture: pre-load before-state for UPDATE/DELETE (extract id arg before `proceed()`), serialize after-state, **sensitive-field exclusion** (§5), `ip_address` capture, explicit **tx boundary** (§7). Core of P4. | Pending rulings §5, §7 |
| **P4.3** | ADMIN-only audit **read endpoint** + repository finders (user/entity/date filters, pagination) + API-SPEC update. | Pending ruling §6 |
| **P4.4** | Retention job / partitioning. | Deferred; needs window §9 |

---

## Rulings summary (all need CTO sign-off before P4.2 starts)

1. **§5** sensitive exclusion — approve `@AuditIgnore` + `captureValues=false`, never serialize raw entity?
2. **§6** read path — build ADMIN-only audit viewer endpoint in P4.3? role = ADMIN?
3. **§7** tx boundary — after-commit (recommended) vs REQUIRES_NEW; success-only auditing OK?
4. **§8** add immutable `actor_role` snapshot column?
5. **§9** retention window (recommend 24 months); purge job vs partitioning?
