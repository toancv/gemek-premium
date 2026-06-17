# Backlog (c) — P2.8: server-derived `mine` filter on GET /api/tickets (BACKEND ONLY)

**Scope:** add an optional server-derived `mine` Boolean filter to the ticket LIST endpoint so an "assigned to the current user" count/list ("Phân công cho tôi") becomes derivable for ALL admin roles. **No `@PreAuthorize` change. No role-scoping change. No FE change** (FE card phase is next). TDD, test-first. Mirrors the P2.6 `overdue` pattern.

Branch `deploy/local`. Pre-change HEAD `0da984c`.

---

## §1 — What changed (production)

| File | Change |
|------|--------|
| `TicketController.java` (`listTickets`) | + `@RequestParam(required = false) Boolean mine`, threaded to the service. `@PreAuthorize` UNCHANGED (`hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')`). |
| `TicketService.java` | interface `listTickets(...)` + `Boolean mine` param. |
| `TicketServiceImpl.java` (`listTickets` + `buildFilterSpec`) | `principalId` + `mine` threaded into `buildFilterSpec`; new Criteria predicate ANDed on top of the existing role-scope spec (`buildScopeSpec`) — scope unchanged. |

## §2 — Server-derived, NOT client-supplied (IDOR-safe)

`mine` is a Boolean flag. The assignee target is `principalId` — the **same caller id `buildScopeSpec` already uses** (`cb.equal(root.get("assignedToUser").get("id"), principalId)`). The FE passes **no user id**; there is nothing to forge. This deliberately avoids the IDOR that an `assigneeId=<id>` param would create (any admin could enumerate another staff member's workload). `principalId` is now also passed into `buildFilterSpec` (previously only `buildScopeSpec` received it) — minor signature change.

`buildFilterSpec` addition (Criteria API):

```java
if (Boolean.TRUE.equals(mine)) {
    // Assigned-to-caller, server-derived from principalId — never a client id (no IDOR).
    // isNotNull + equal so an UNASSIGNED ticket (assignedToUser NULL) can never match.
    predicates.add(cb.and(
            cb.isNotNull(root.get("assignedToUser").get("id")),
            cb.equal(root.get("assignedToUser").get("id"), principalId)));
}
```

## §3 — Null-safety (unassigned excluded)

`assigned_to_user_id` is nullable. The predicate is built via **Criteria API**, never JPQL with a nullable param. `cb.isNotNull(assignedToUser.id)` guards `cb.equal` so a NULL assignee is a definite non-match — an unassigned ticket can never satisfy `mine=true`. (The `root.get("assignedToUser").get("id")` path also triggers a default INNER join, which independently drops unassigned rows; the explicit `isNotNull` documents the intent and is belt-and-suspenders.) Proven by `mineTrue_returnsOnlyTicketsAssignedToCaller` (the `tUnassigned` row is excluded).

## §4 — `mine=false` semantics (chosen)

**Chosen: `false` = NO-OP (no assignee filtering — identical to absent).** Only `mine=true` is active. Rationale (per the investigation §4): "not assigned to me" is not a product need and would muddy the «Phân công cho tôi ✕» chip; YAGNI. Guarded with `Boolean.TRUE.equals(mine)` so both `false` and `null`/absent skip the predicate entirely — existing behavior byte-for-byte unchanged. Tested by `mineFalse_isNoOp_returnsAll` (returns all, same as absent). **This intentionally differs from P2.6's `overdue=false` complement** — documented divergence, decision logged in DECISIONS.md.

## §5 — Scope composition per role

`mine` is ANDed on top of `buildScopeSpec` via `spec = buildScopeSpec(...).and(buildFilterSpec(...))` — never replaces scope.

| Role | Scope spec | `mine=true` AND scope | Net |
|------|-----------|----------------------|-----|
| ADMIN / BOARD_MEMBER | `conjunction()` (unrestricted) | `assignedToUser.id == me` across all tickets | all tickets assigned to me |
| TECHNICIAN | `assignedToUser.id == me OR status == NEW` | `(assigned-to-me OR NEW) AND assigned-to-me` = `assigned-to-me` | **strict subset** — NEW arm collapses, never bypasses scope |
| RESIDENT | household / community | `... AND assigned-to-me` | effectively empty (residents are not assignees) |

Confirmed by `mineTrue_respectsTechnicianRoleScope`: a technician with `mine=true` sees their own assigned ticket; a NEW-unassigned ticket (in base scope) AND another technician's ticket both drop out.

## §6 — `mine` × `overdue` compose

The two predicates AND independently. `mineTrueOverdueTrue_returnsCallerOwnOverdueOpen`: of three fixtures (mine+overdue, mine+future, other+overdue), `?mine=true&overdue=true` returns exactly the caller's own overdue-open ticket — the non-overdue mine ticket and the overdue not-mine ticket are both excluded.

## §7 — Tests (TDD, written first; RED→GREEN)

Added to `TicketLifecycleIntegrationTest` (reuses the suite's per-test unique-block isolation; assertions scoped by `apartmentId` / self-created ids, never floating absolute counts — shared dev-DB pollution). `TicketPublicAccessTest` got the forced 2-call-site signature adaptation (`+ null` for `mine`) to keep the suite compiling.

| Test | Asserts |
|------|---------|
| `mineTrue_returnsOnlyTicketsAssignedToCaller` | 3 tickets (assigned-to-caller, assigned-to-other, unassigned) → `?mine=true` returns exactly the caller's one; other-user's and UNASSIGNED excluded; `total==1`. |
| `mineAbsent_returnsAll_unchangedBehavior` | same shape, no `mine` → all three returned (regression guard). |
| `mineFalse_isNoOp_returnsAll` | `?mine=false` → all three returned (chosen no-op semantics). |
| `mineTrue_respectsTechnicianRoleScope` | technician `?mine=true` sees own-assigned; NEW-unassigned (base scope) and another tech's ticket both drop out — strict subset. |
| `mineTrueOverdueTrue_returnsCallerOwnOverdueOpen` | `?mine=true&overdue=true` → only the caller's own overdue-open ticket. |

**RED proof:** before the production change, the three positive-filter tests failed (param ignored — list returned all): `mineTrue_returnsOnlyTicketsAssignedToCaller` `expected: <1> but was: <3>`. **GREEN after:** 5/5 pass.

## §8 — Verification

- **Full backend suite: `319 / 319` pass, BUILD SUCCESS** (`backend/mvnw.cmd test`). Was 314; +5 new mine tests.
- **HTTP + DB cross-check (dev `gemek` DB, live :80 — OLD image):**
  - **DB (canonical):** `SELECT count(*) FROM tickets WHERE assigned_to_user_id = '<admin id 93c3d04d…>'` → **23** (of **705** total). This is what an ADMIN `?mine=true` must return after redeploy.
  - **Live (running backend, OLD image, nginx :80):** `GET /api/tickets?mine=true&size=1` as admin → `total=705` == no-filter `total=705` → confirms the **old code ignores `mine`** (baseline; param absent pre-change). Mirrors P2.6's old-image overdue baseline.
  - **New-code HTTP path:** proven by the 5 integration tests — a real HTTP `GET /api/tickets?mine=true` through controller→`@PreAuthorize`→`buildFilterSpec` Criteria→Postgres returns exactly the assigned-to-caller set.
  - **Post-deploy expectation (for the CTO :80 smoke):** after the docker backend is rebuilt with this change, `GET /api/tickets?mine=true` as the admin must return `total=23`. The live literal-23 check is deferred to the gated redeploy (running container is the old image), consistent with the pending P2.5/P2.6 :80 smokes.

## §9 — Commits

1. `feat(ticket): add server-derived mine filter to GET /api/tickets` — 3 production files + the forced `TicketPublicAccessTest` signature adaptation (keeps the suite compiling). Committed feat-first so every commit is green (a test-first commit would be RED, violating never-commit-broken-code); TDD RED→GREEN proven in the working tree before committing (§7).
2. `test(ticket): integration tests for mine filter (true/false/absent + scope + mine×overdue)` — the 5 new tests + `UserRepository` autowire.
3. `docs(context): P2.8 mine filter — PROGRESS + API-SPEC + report`.

**FE card phase is NEXT** ("Phân công cho tôi" StatCard via `useTicketCount({mine:true})` + `/tickets?mine=true` drill-down + new «Phân công cho tôi ✕» clearable chip). Not started — awaiting CTO go.
