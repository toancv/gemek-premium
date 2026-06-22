# AUD.3 — Remove AuditLogAspect + @Auditable (auditing consolidated on created_by/updated_by)

**Type:** Refactor (deletion) + test. **Date:** 2026-06-22. **Branch/HEAD:** `deploy/local` @ `701ade8` (pre-change).
**Plan:** `reports/audit-columns-investigation.md` §5 (blast radius) + §C (removal plan). **DECISIONS:** "AUD.3 — AuditLogAspect + @Auditable removed".
**Tree state at start:** clean for code (only untracked `reports/*` + `scripts/GenHash.java`; zero tracked modifications). **Baseline suite: 336/336 green.**

This phase **completes the AUD chain** (AUD.1 foundation → AUD.2 Contract/Announcement converge → AUD.3 aspect removal).

---

## What was REMOVED

| File | Change |
|------|--------|
| `common/audit/AuditLogAspect.java` | **DELETED** — the `@Aspect @Component` that wrote `audit_logs` rows fire-and-forget for the 4 `@Auditable` user actions. |
| `common/audit/Auditable.java` | **DELETED** — the `@interface` annotation. Removed only after confirming its 4 usages were gone (grep `@Auditable\(` = 0 remaining). |
| `module/user/UserServiceImpl.java` | Removed the `import vn.vtit.gemek.common.audit.Auditable;` and the 4 `@Auditable(...)` annotations on `createUser` (CREATE), `updateUser` (UPDATE), `deactivateUser` (DELETE), `resetPassword` (RESET_PASSWORD). Method bodies unchanged. |

**Why removable:** create/update attribution is now captured system-wide via Spring Data `created_by`/`updated_by` (`@CreatedBy`/`@LastModifiedBy` on `AuditableEntity`/`CreatableEntity` + `SecurityAuditorAware`, landed in AUD.1/AUD.2). The aspect is redundant for that purpose.

## What was KEPT (NOT dropped — destructive/irreversible)

| Artifact | Status |
|----------|--------|
| `audit_logs` **table** + `V10` migration | **Untouched.** Historical rows preserved. No DROP migration added — that is a separate future CTO decision. |
| `AuditLog` **entity** | **Retained**, javadoc updated to mark it **write-idle** (table still exists; orphaning the mapping would be odd). |
| `AuditLogRepository` | **Retained** for read access to preserved history; javadoc updated to note write-idle. Was injected ONLY by the deleted aspect → no other caller breaks. |

## INTENTIONALLY no longer captured (knowing CTO trade-off — NOT a regression)

Recorded in `reports/audit-columns-investigation.md` §"CTO rulings" (line 14): the CTO ruled to remove the aspect once `created_by`/`updated_by` lands, **knowingly giving up**:

1. **Reset-password actor attribution** — `RESET_PASSWORD` wrote no entity column, only an `audit_logs` row. With the aspect gone, *who reset a password* is no longer recorded anywhere.
2. **Full before/after change-history** — the aspect's per-action `audit_logs` rows (action label + entity id) are no longer written. `created_by`/`updated_by` capture only the *latest* create/update actor, not a change log.

## Blast radius — confirmed before deleting

`reports/audit-columns-investigation.md` §5 mapped `common.audit` / `@Auditable` to exactly 5 files; re-verified by grep at impl time:

- `@Auditable` annotation used **only** in the 4 `UserServiceImpl` sites (grep `@Auditable\(` + `import ...Auditable;`).
- `AuditLogRepository` injected **only** by `AuditLogAspect` (no other constructor/field).
- The aspect's `SecurityContextHolder` read (`:117-127`) was self-contained — **nothing else depended on the aspect running**. `SecurityAuditorAware` (AUD.1) is now the SecurityContext reader for auditing; it mirrors the same logic. Removing the aspect breaks no other behavior.
- Only one stale doc reference remains: `SecurityAuditorAware.java` javadoc says it "mirrors `{@code AuditLogAspect.resolveActorId()}`" — plain `{@code}` text (no `@link`, no compile/javadoc dependency), kept as accurate lineage. `AuditLog.java`/`AuditLogRepository.java` had their javadoc updated to drop the aspect `@link` and state write-idle.

## Tests

- **No test asserted on `audit_logs` rows** — grep `AuditLog|audit_logs|AuditLogRepository` over `backend/src/test` returned **0 files**. So there were no audit-row assertions to remove or rewrite (the task's "update tests off audit-row assertions" had nothing to change).
- **Added 3 unit tests** to `UserServiceImplTest` (pure Mockito on `UserServiceImpl` — the aspect never participated here, so these prove the business path works aspect-free):
  - `deactivateUser_validTarget_deactivatesUser` — valid target → `isActive()==false` after save.
  - `resetPassword_validUser_savesNewHash` — re-encodes and saves the new hash.
  - `resetPassword_userNotFound_throwsNotFound` — NOT_FOUND on missing user.
  - `createUser` / `updateUser` were already covered. All 4 actions now have a "still works" guard.

## Verify

- **Full backend suite (`backend/mvnw.cmd test`): 339/339 green, 0 failures, 0 errors, BUILD SUCCESS** (336 baseline + 3 new). Aggregated from `target/surefire-reports`.
- **Flyway:** no new migration in AUD.3 (unchanged).
- **HTTP-layer smoke (equivalent):** `UserControllerTest` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` + real login) and `AuditingActorCaptureIntegrationTest` both boot the **real ApplicationContext** and pass — proving no orphaned aspect bean / broken AOP wiring after deletion, and that create-user via the full HTTP stack still succeeds. A live docker curl smoke would be redundant over a green full-context suite; not run.
- **/code-review:** clean — no findings (verified no dangling references, new tests assert what their names claim, entity/repo still valid).

## API-SPEC

**No change.** No endpoint path/method/param/request/response shape/`@PreAuthorize` was touched.

## Commits (this phase)

1. `refactor(audit): remove AuditLogAspect + @Auditable (consolidate on created_by/updated_by)`
2. `test(audit): add UserServiceImpl tests proving 4 user actions work without aspect`
3. `docs(context): AUD.3 report + PROGRESS + DECISIONS (AUD chain complete; API-SPEC unchanged)`
