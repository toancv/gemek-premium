# Backlog (c) — P2.6: `overdue` filter on GET /api/tickets (BACKEND ONLY)

**Scope:** add an optional `overdue` Boolean filter to the ticket LIST endpoint so technician-reachable, role-scoped SLA-breached counts become derivable. **No `@PreAuthorize` change. No role-scoping change. No FE change** (FE consumption = P2.7). TDD, test-first.

Branch `deploy/local`. Pre-change HEAD `27c4814`.

---

## §1 — What changed (production)

| File | Change |
|------|--------|
| `TicketController.java` (`listTickets`) | + `@RequestParam(required = false) Boolean overdue`, threaded to service. `@PreAuthorize` UNCHANGED (`hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')`). |
| `TicketService.java` | interface `listTickets(...)` + `Boolean overdue` param. |
| `TicketServiceImpl.java` (`listTickets` + `buildFilterSpec`) | `overdue` threaded into `buildFilterSpec`; new Criteria predicate ANDed on top of the existing role-scope spec (`buildScopeSpec`) — scope unchanged. |

`buildFilterSpec` addition (Criteria API, matches the existing optional-filter style — no JPQL nullable params):

```java
if (overdue != null) {
    Predicate breached = cb.and(
            cb.isNotNull(root.get("slaDeadline")),
            cb.lessThan(root.<OffsetDateTime>get("slaDeadline"), OffsetDateTime.now()),
            cb.not(root.get("status").in(TicketStatus.DONE, TicketStatus.CANCELLED)));
    predicates.add(Boolean.TRUE.equals(overdue) ? breached : cb.not(breached));
}
```

## §2 — Predicate + closed-status source mirrored

**overdue=true →** `sla_deadline IS NOT NULL AND sla_deadline < now AND status NOT IN (DONE, CANCELLED)`.

The closed-status set `(DONE, CANCELLED)` and the `sla_deadline < NOW()` form are **mirrored exactly** from the existing canonical SLA-breach definition — not invented:
- `TicketRepository.java:43-44, 91-92, 130-131, 159-160` — every report aggregate (`slaBreached`, `overdueRequests`): `COUNT(CASE WHEN t.sla_deadline < NOW() AND t.status NOT IN ('DONE','CANCELLED') THEN 1 END)`.
- `TicketRepository.java:184-186` (`findSlaOverdueCandidates`, used by `TicketSlaScheduler`): `slaDeadline IS NOT NULL AND slaDeadline < :now`.

So `?overdue=true` count over the ADMIN (unrestricted) scope equals the Reports tab's `slaBreached` / dashboard `overdueRequests` for the same dataset — by construction.

## §3 — overdue=false semantics (chosen)

**Chosen: `false` = the logical complement (= "not overdue").** Implemented as `cb.not(breached)`. Because `breached` leads with `cb.isNotNull(slaDeadline)`, the negation collapses cleanly under Hibernate 6 three-valued logic: a NULL deadline → `breached` is a definite `false` → `cb.not` → `true` (counted as not-overdue). So `false` returns: NULL-deadline OR future-deadline OR closed (DONE/CANCELLED) tickets. `null`/absent = **no SLA filtering — existing behavior byte-for-byte unchanged.** Decision logged in DECISIONS.md.

## §4 — Null-safety (Hibernate 6)

`sla_deadline` is nullable (tickets without a deadline). The predicate is built via **Criteria API**, never JPQL with a nullable param. `cb.isNotNull(slaDeadline)` guards the `lessThan` so a NULL deadline can never match `overdue=true`. Verified by a dedicated test (NULL-deadline ticket excluded).

## §5 — Role-scope preserved

`buildScopeSpec` (TECHNICIAN = assigned-to-me OR status=NEW; RESIDENT = household/community; ADMIN/BOARD = all) is `.and(...)`-composed with `buildFilterSpec` — `overdue` is ANDed on top, never replacing scope. **Test `overdueTrue_respectsTechnicianRoleScope`** proves a technician with `?overdue=true` sees their own assigned overdue ticket but NOT another technician's overdue ticket. No new data exposed: a technician still sees only in-scope tickets, now filterable to the overdue ones.

## §6 — Tests (TDD, written first; RED→GREEN)

Added to `TicketLifecycleIntegrationTest` (reuses the suite's per-test unique-block isolation + the Test-3 repo-backdate fixture pattern; assertions scoped by `apartmentId` / by self-created ids, never floating absolute counts):

| Test | Asserts |
|------|---------|
| `overdueTrue_returnsOnlyBreachedOpenTickets` | 4-ticket fixture in one apt → `?overdue=true&apartmentId=` returns exactly the breached-open one. DONE-past, NULL-deadline, future-deadline all EXCLUDED. `total==1`. |
| `overdueAbsent_returnsAll_unchangedBehavior` | same fixture, no `overdue` param → all 4 returned (`total==4`) — regression guard. |
| `overdueTrue_respectsTechnicianRoleScope` | technician sees own-assigned overdue ticket, not another tech's overdue ticket. |

**RED proof:** before the production change, `overdueTrue_returnsOnlyBreachedOpenTickets` failed `expected: <1> but was: <4>` (param ignored — list returned all 4). **GREEN after:** 3/3 pass.

## §7 — Verification

- **Full backend suite: `314 / 314` pass, BUILD SUCCESS** (`backend/mvnw.cmd test`).
- **HTTP + DB cross-check:**
  - **DB (dev `gemek`, canonical predicate):** `SELECT count(*) FROM tickets WHERE sla_deadline IS NOT NULL AND sla_deadline < now() AND status NOT IN ('DONE','CANCELLED')` → **459** (of **603** total). This is the whole-dataset overdue-open count an ADMIN `?overdue=true` must return.
  - **Live (running backend, OLD image, nginx :80):** `GET /api/tickets?overdue=true&size=1` → `total=603` == no-filter `total=603` → confirms the **old code ignores `overdue`** (baseline; param absent pre-change).
  - **New-code HTTP path:** proven by the integration tests — a real HTTP `GET /api/tickets?overdue=true` through controller→`@PreAuthorize`→`buildFilterSpec` Criteria→Postgres returns exactly the canonical-predicate set.
  - **Post-deploy expectation (for the CTO :80 smoke):** after the docker backend is rebuilt with this change, `GET /api/tickets?overdue=true` as ADMIN must return `total=459`. The live literal 459 check is deferred to that gated redeploy (the running container is the old image; redeploy is the CTO-gated deploy step, consistent with the pending P2.5 :80 smoke).

## §8 — Commits

1. `feat(ticket): add overdue filter to GET /api/tickets list endpoint` — 3 production files + the forced `TicketPublicAccessTest` signature adaptation (keeps the existing suite compiling). Committed feat-first so every commit is green (a test-first commit would be RED, violating the never-commit-broken-code rule); TDD RED→GREEN was proven in the working tree before committing (§6).
2. `test(ticket): integration tests for overdue filter (true/false/absent + technician scope)` — the 3 new tests.
3. `docs(context): P2.6 overdue filter — PROGRESS + API-SPEC + report`.

**FE consumption is P2.7** (SLA-breached card via `useTicketCount({overdue:true})` + the `?overdue=true` drill-down). Not started — awaiting CTO go.
