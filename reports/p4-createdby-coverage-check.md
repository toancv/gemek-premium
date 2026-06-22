# P4 — created_by / updated_by Coverage Check (does it make audit_logs redundant?)

**Mode:** READ-ONLY. No code modified, no aspect removed.
**Branch:** `deploy/local` @ `b664ad3` — tree clean (only untracked `reports/`, `scripts/GenHash.java`).
**Date:** 2026-06-18

## Headline

**NO. `created_by` / `updated_by` do NOT exist on the `User` entity.** The premise of this check —
"User already records who did it via created_by/updated_by" — is **false for User**. All 4 audited
actions are User operations, so **none of them are covered by an actor column**, because no such column
exists on User. P4 cannot be dropped on the grounds that created_by/updated_by already capture the actor.

## §1 — Auditing columns on `User` (`module/user/entity/User.java`)

- **Timestamps only:** `created_at` (`:92`, `@PrePersist onCreate` `:102`) and `updated_at` (`:96`,
  `@PreUpdate onUpdate` `:111`). These store **WHEN**, set by JPA lifecycle callbacks — not by Spring Data.
- **No actor columns:** there is **no `created_by`, `updated_by`, or `modified_by`** field on User.
- **No Spring Data auditing anywhere:** grep for `@EnableJpaAuditing`, `@CreatedBy`, `@LastModifiedBy`,
  `@CreatedDate`, `@LastModifiedDate`, `@EntityListeners(AuditingEntityListener)` → **0 hits** in the repo.
- **Where created_by/updated_by DO exist:** only on **other** domains — `Contract.java` /
  `V6__create_contracts.sql` and `Announcement.java` / `V7__create_announcements.sql`. **Not User.** Those
  do not cover the 4 User actions in question.

→ User records timestamps (WHEN) but **never the actor (WHO)**.

## §2 — Coverage of the 4 audited actions (all User)

| Action | Sets `updated_at`? | Records WHO (actor)? | Covered by created_by/updated_by? |
|--------|--------------------|----------------------|-----------------------------------|
| `createUser` | yes (`@PrePersist`) | **no** — no created_by column | **no** |
| `updateUser` | yes (`save`→`@PreUpdate`) | **no** — no updated_by column | **no** |
| `deactivateUser` | yes (`save`→`@PreUpdate`) | **no** | **no** |
| `resetPassword` | yes (`save`→`@PreUpdate` bumps `updated_at`) | **no** | **no** |

Note on `resetPassword`: it *does* call `save()` so `updated_at` changes (the timestamp moves), but there
is still **no actor recorded** — and the timestamp bump is indistinguishable from a profile edit. So even
the "something changed" signal is ambiguous, and "who reset the password" is **invisible** without the aspect.

## §3 — The limitation (moot here, since the columns don't exist)

The classic created_by/updated_by trade-off — they hold only the **most-recent** actor (overwritten each
change), preserving no history the way `audit_logs` rows do — **does not even apply to User**, because User
has no such columns. For User the gap is more basic: **zero actor attribution** today. `audit_logs` is the
**only** mechanism that records who performed any of the 4 actions.

## Conclusion — what is LOST if P4 / the aspect were dropped

| Action | Covered by created_by/updated_by? | Visibility LOST if aspect removed |
|--------|-----------------------------------|-----------------------------------|
| createUser | **no** | who created which account |
| updateUser | **no** | who edited a profile (incl. role changes) |
| deactivateUser | **no** | who deactivated which account |
| **resetPassword** | **no (decisive)** | **who reset whose password — fully UNAUDITED; sensitive admin action with no trail at all** |

**Can P4 be dropped with created_by/updated_by as substitute?** **No** — those columns do not exist on
User, so they substitute for nothing here. If the aspect were later removed, **all 4 User actions become
completely unattributed**, with `resetPassword` (a sensitive admin power) the most serious blind spot.

## For CTO ruling (not recommending — just exposing coverage)

- **(a) Drop P4 entirely:** would leave all 4 User actions with no actor trail. Only viable if the org
  accepts zero attribution for admin user-management, OR if it first adds Spring Data `@CreatedBy/@LastModifiedBy`
  to User (which still gives only most-recent actor, not history, and still misses nothing on resetPassword
  only if password reset goes through an audited save — it does, but without an actor column).
- **(b) Drop P4 before/after work but keep auditing `resetPassword`:** preserves the most sensitive trail
  (who reset whose password) at minimal cost; the aspect already persists action rows for it.
- **(c) Proceed with P4:** full before/after across the 4 actions per the prior investigation.

The aspect currently persists rows and was **not** touched. No removal performed — this is information for
the CTO to rule on.
