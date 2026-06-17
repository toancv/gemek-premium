# Backlog (c) — Investigation: "Phân công cho tôi" card + assignee filter on GET /api/tickets

**Type:** READ-ONLY investigation. No code changed. Branch `deploy/local`, HEAD `0e192bc`, tree clean (only untracked `reports/*` diagnostics).

**Question:** a "Phân công cho tôi" (assigned-to-me) stat card + an assignee filter on `GET /api/tickets`, for ALL admin roles. Does the assignee filter already exist in the BE, or is a BE change required?

**Decisive answer up front:** the assignee filter does **NOT exist** today. A gated BE phase is required first (P2.6-style, TDD), then the FE card phase.

---

## §1 — Does an assignee filter exist today? → NO

`GET /api/tickets` accepts exactly these filter params (`TicketController.java:105-114`):
`status` (List), `visibility`, `category`, `priority`, `apartmentId`, `overdue`, `page`, `size`.

They thread into `buildFilterSpec(statuses, category, priority, apartmentId, overdue)` (`TicketServiceImpl.java:993-1027`). The predicate list there covers **only** status / category / priority / apartmentId / overdue. **No `assignee` / `assignedTo` / `mine` param exists** — neither on the controller, the `TicketService` interface signature (`listTickets(...)`), nor in `buildFilterSpec`.

→ **A BE change is required** (gated). It mirrors exactly how P2.6 added `overdue`.

**Assignee field (the predicate target):** `Ticket.assignedToUser` — `@ManyToOne(fetch = LAZY, optional = true)`, FK `assigned_to_user_id`, nullable (`Ticket.java:108-110`). There is also `assignedToContractor` (external company, `Ticket.java:117-119`) — irrelevant to "assigned to **me**" since a contractor is not a logged-in user.
**How assignment is set:** `PUT /api/tickets/{id}/assign` → `assignTicket(...)` sets `assignedToUser` (`TicketServiceImpl.java:447-452`). Endpoint is **ADMIN-only** (`@PreAuthorize("hasRole('ADMIN')")`, `TicketController.java:208`).
→ The predicate for "assigned to me" is therefore: `assignedToUser.id == <caller id>`.

## §2 — "Phân công cho tôi" semantics → server-derived `mine=true` flag (recommended)

**The caller id is already available to the list query.** `listTickets` controller passes `principal.getId()` as `principalId` (`TicketController.java:120-122`); the service already uses it in `buildScopeSpec` (`TicketServiceImpl.java:954-980`). So "mine" needs **no client-supplied id** — express it as a server-derived boolean flag `mine=true`, resolved against the principal.

**Recommendation: `mine` Boolean flag, NOT an explicit `assigneeId` param.** Rationale:
- The principal id is already threaded — mirrors how `buildScopeSpec` derives the caller (TECHNICIAN scope does exactly `cb.equal(root.get("assignedToUser").get("id"), principalId)`, `TicketServiceImpl.java:957`). Reusing that pattern is the right altitude.
- No IDOR surface: a `mine` flag can only ever mean the caller's own id; an `assigneeId` param would let any admin enumerate another staff member's workload — out of scope for this card and a needless widening.
- YAGNI: "filter by arbitrary assignee X" is a different, future feature. If it ever lands, add `assigneeId` then; do not pre-build it.

**How `mine=true` composes per role** (ANDed on top of `buildScopeSpec`, identical to how `overdue` composes — `spec = buildScopeSpec(...).and(buildFilterSpec(...))`, `TicketServiceImpl.java:212-213`):

| Role | Scope spec | `mine=true` AND scope | Net |
|------|-----------|----------------------|-----|
| ADMIN / BOARD_MEMBER | `conjunction()` (unrestricted) | `assignedToUser.id == me` across all tickets | all tickets assigned to me |
| TECHNICIAN | `assignedToUser.id == me OR status == NEW` | `(assignedToUser.id == me OR status==NEW) AND assignedToUser.id == me` = `assignedToUser.id == me` | **clean subset** of existing scope — never bypasses it, exposes no new rows |
| RESIDENT | household / community | `... AND assignedToUser.id == me` | effectively empty (residents are not assignees) — harmless |

→ For TECHNICIAN the filter is a **strict subset** of the role-scope (the `status=NEW` arm drops out under the AND). No scope bypass; no new data. Confirmed.

## §3 — The card + drill-down → feasible, same mechanism as P2.5/2.7

- **Count source:** the card sources its number via `useTicketCount({ mine: true })` → `GET /api/tickets?mine=true&size=1` reading `total` — byte-for-byte the same mechanism the P2.7 SLA-breached card uses for `overdue:true`. Feasible once §4 lands.
- **Drill-down:** clicking the card → `/tickets?mine=true`, seeding the existing `useSearchParams` filter (wired in P2.7).
- **New clearable chip required:** there is no dropdown control for an assignee filter (unlike status/category/priority which have selects). So `mine` must surface as a dedicated clearable chip — «Phân công cho tôi ✕» — exactly like the overdue «Trễ hạn ✕» chip. Note for the FE phase.

## §4 — BE change scope (minimal, mirrors P2.6 `overdue` pattern)

If approved, the BE phase is:
1. **Controller** (`listTickets`): `+ @RequestParam(required = false) Boolean mine`, threaded to the service. `@PreAuthorize` **UNCHANGED** (`hasAnyRole('ADMIN','TECHNICIAN','RESIDENT','BOARD_MEMBER')`) — this is a filter, not a permission change.
2. **`TicketService` interface**: `listTickets(...)` `+ Boolean mine`.
3. **`TicketServiceImpl`**: thread `mine` into `buildFilterSpec`; add one Criteria predicate ANDed on top of scope:
   ```java
   if (Boolean.TRUE.equals(mine)) {
       // Assigned-to-caller. principalId already available (used by buildScopeSpec).
       // cb.equal on the FK id naturally excludes unassigned tickets: a NULL
       // assignedToUser.id is never equal to a non-null id (SQL three-valued logic).
       predicates.add(cb.equal(root.get("assignedToUser").get("id"), principalId));
   }
   ```
   `principalId` must be passed into `buildFilterSpec` (today only `buildScopeSpec` gets it) — minor signature change. `mine` null/false/absent = **no filtering, existing behavior unchanged**. (Note: treat only `mine=true` as active; `mine=false` should be a no-op, NOT a complement — "not assigned to me" is not a product need and would muddy the chip.)
4. **Null-safety:** `assigned_to_user_id` is nullable. The `cb.equal(...id, principalId)` excludes unassigned tickets by construction (NULL ≠ id). No `isNotNull` guard needed, but a test must prove it.
   - ⚠️ **Join-type caveat [TODO: verify in impl]:** `root.get("assignedToUser").get("id")` triggers a default **INNER** join in JPA Criteria, which also drops unassigned rows — fine for `mine=true` (we *want* them dropped). But it must not accidentally affect other predicates in the same spec. The existing TECHNICIAN scope already navigates this same path inside an `OR` and works, so the pattern is proven; confirm during impl that adding it to `buildFilterSpec` doesn't change row counts when `mine` is absent.

**Test cases (TDD, RED→GREEN, mirror P2.6's three tests):**
- `mineTrue_returnsOnlyTicketsAssignedToCaller` — fixture with one ticket assigned to caller, one to another user, one unassigned → `?mine=true` returns exactly the caller's one; other-user's and unassigned EXCLUDED; `total==1`.
- `mineAbsent_returnsAll_unchangedBehavior` — same fixture, no `mine` → all returned (regression guard).
- `mineTrue_respectsTechnicianRoleScope` — technician sees own-assigned ticket but the `status=NEW`-only rows drop out under the AND (proves subset, no bypass).
- (optional) `mineTrue_excludesUnassigned` — explicit NULL-assignee exclusion.

## §5 — Recommendation

**Needs a gated BE phase first — this is NOT FE-only.** The assignee filter does not exist on `GET /api/tickets` (§1).

Sequence (same shape as P2.6 → P2.7):
1. **BE phase (CTO-gated, TDD via java/springboot-tdd):** add the server-derived `mine` Boolean filter per §4. No `@PreAuthorize` change, no scope change. ~3 production files + 3-4 tests. → CTO approves.
2. **FE card phase:** "Phân công cho tôi" StatCard via `useTicketCount({mine:true})` + `/tickets?mine=true` drill-down + new «Phân công cho tôi ✕» clearable chip (§3).

**Decision pending CTO ruling** — do not implement.
