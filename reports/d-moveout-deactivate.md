# Backlog (d) follow-up — Conditional user deactivation on final move-out

**Date:** 2026-06-22
**Branch:** deploy/local
**Scope:** `ResidentServiceImpl.moveOut` (BE), `ResidentsPage.tsx` move-out dialog copy (FE), tests, docs.

## 1. Relationship findings (verified before coding)

| Relationship | Cardinality | Evidence |
|---|---|---|
| Resident → User | `@ManyToOne` — one User can have MULTIPLE Resident records | `Resident.java:54-56` (`@ManyToOne` on `user`, `user_id NOT NULL`) |
| Active uniqueness | At most ONE active (moveOutDate == null) resident per user | `Resident.java:36-38` Javadoc — partial unique index `uq_residents_active_user` |
| Resident → Apartment | `@ManyToOne` — one Resident record = one Apartment | `Resident.java:59-61` |
| "Active residency" | `moveOutDate == null` | `Resident.java:80-81`; query `existsActiveByUserId` `ResidentRepository.java:62-67` |

**Conclusion:** model is clean, NOT tangled. A user may own several historical resident rows (multi-residency over time / across apartments) but only one can be active at a time. The user account is the single login identity; residency is what we gate on. No blocker — the safe "no remaining active residency" rule applies directly.

## 2. The conditional rule (implemented)

In `moveOut`, in the SAME transaction, AFTER the existing move-out logic (set `moveOutDate`, clear `primaryContact`, append `MOVED_OUT` history):

```
User residentUser = saved.getUser();
if (residentUser != null && !residentRepository.existsActiveByUserId(residentUser.getId())) {
    residentUser.setActive(false);
    userRepository.save(residentUser);
}
```

- The move-out save already set `moveOutDate`, so `existsActiveByUserId` (auto-flush before the JPQL query) no longer counts THIS residency. It therefore answers exactly: "does the user still have ANY OTHER active residency?"
- Deactivate ONLY when the answer is no. A user who still lives in another apartment keeps their login (safe guard).
- For the current 1-active-residency reality this means "deactivate on move-out", but it is implemented via the residency check so it stays correct if multi-residency arises.
- `resident.user` is `NOT NULL` per schema; the `!= null` guard is defensive (a resident with no linked user is never touched).

## 3. Reused deactivation mechanism — what and why

**Decision: set `user.active = false` directly on the entity (consistent with `createResident`'s `user.setActive(true)` at `ResidentServiceImpl.java:157`), NOT via `UserServiceImpl.deactivateUser`.**

Reasons:
- `UserServiceImpl.deactivateUser(id, requestUserId)` (`UserServiceImpl.java:162-177`) has a `SELF_OPERATION_NOT_ALLOWED` guard — it throws if `id == requestUserId`. Inside move-out the actor is `principalId`; if an actor ever moved out their own residency, that guard would wrongly abort a valid move-out. The guard is meant for the admin UsersPage flow, not move-out.
- That method's ONLY behavior is `user.setActive(false); userRepository.save(user)`. There is **no token-revocation or other side-effect** in the codebase's deactivation path (auth is stateless JWT — a deactivated user is rejected by the active-flag check, same as the admin-driven deactivate). So nothing extra needs preserving.
- `ResidentServiceImpl` already owns `userRepository` and manipulates `user.active` directly (create path). Direct entity set is the established in-service pattern and avoids the cross-module self-op guard.

Net effect is identical to the UsersPage deactivate (active flag flipped, same login-gating consequence), without the inapplicable self-op guard.

## 4. Atomicity

`moveOut` is `@Transactional`. The deactivation runs inside the same transaction. If `userRepository.save(user)` throws, the `RuntimeException` propagates out of `moveOut`, Spring rolls the transaction back, and `move_out_date` is NOT committed — the move-out and the deactivation succeed or fail together. Proven by `moveOut_deactivationThrows_propagates` (exception propagates; mapper never reached → method aborted before commit/return).

## 5. Tests (TDD; full suite green)

Added to `ResidentServiceImplTest`:
- `moveOut_noOtherActiveResidency_deactivatesUser` — `existsActiveByUserId == false` → `user.active` becomes false, `userRepository.save(user)` called.
- `moveOut_anotherActiveResidency_keepsUserActive` — `existsActiveByUserId == true` → user stays active, `save(user)` never called (safe guard; multi-residency is possible per finding #1).
- `moveOut_noLinkedUser_noDeactivation` — `resident.user == null` → no error, no deactivation, residency-check query never run.
- `moveOut_deactivationThrows_propagates` — deactivation throws → move-out propagates the exception (rolls back), response never mapped.

Existing move-out tests (`already moved out`, `not found`) still pass.

**Backend suite: 343/343 green** (`ResidentServiceImplTest`: 16/16).

## 6. FE copy update

`ResidentsPage.tsx` move-out dialog (lines ~364-368). Old copy claimed the action *KHÔNG khoá tài khoản đăng nhập* (does NOT lock the login). Since the BE behavior changed, the copy now states the truth, accurate to the conditional rule:

> "Tài khoản đăng nhập **sẽ bị khoá** nếu cư dân không còn cư trú ở căn hộ nào khác."

Admin build (`tsc && vite build`): green, 590 modules.

## 7. API-SPEC

`POST /api/residents/{id}/move-out` description updated to note the conditional user-account deactivation as a documented side effect of the endpoint.
