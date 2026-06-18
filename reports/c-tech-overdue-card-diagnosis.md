# Backlog (c) — Diagnosis: technician «Trễ hạn» (overdue) card shows tickets not assigned to the technician

**Type:** READ-ONLY diagnosis. No code changed, no fix proposed yet. Branch `deploy/local`, HEAD `c83f592`, tree clean (only untracked `reports/*`).

**Symptom reported:** on the admin TicketsPage, a TECHNICIAN's «Trễ hạn» stat card counts tickets that are NOT assigned to that technician — contradicts the expected "my work" role-scope.

**Verdict up front: NOT A BUG. Correct, scope-preserving business behavior.** The "not mine" rows are **NEW-and-overdue** tickets in the shared claimable queue, legitimately in technician scope via the `OR status=NEW` arm. Scope-leak bucket (iii) = **0** for every technician tested and **0 by construction**. The real issue is **LABEL/SEMANTICS**, not security.

---

## §1 — How the card's query is built (file:line)

- **Composition is always `scope AND filter`** — `TicketServiceImpl.java:212-213`:
  ```java
  Specification<Ticket> spec = buildScopeSpec(principalId, role, visibility)
          .and(buildFilterSpec(principalId, statuses, category, priority, apartmentId, overdue, mine));
  ```
  Scope is NEVER dropped — the overdue predicate is ANDed on top of it.
- **TECHNICIAN scope** — `buildScopeSpec`, `TicketServiceImpl.java:956-960`:
  ```java
  cb.or(cb.equal(root.get("assignedToUser").get("id"), principalId),
        cb.equal(root.get("status"), TicketStatus.NEW))
  ```
  → `assignee == me OR status == NEW`.
- **overdue predicate** — `buildFilterSpec`, `TicketServiceImpl.java:1017-1028`:
  ```java
  cb.and(cb.isNotNull(slaDeadline), cb.lessThan(slaDeadline, now), cb.not(status IN (DONE, CANCELLED)))
  ```
  Excludes DONE/CANCELLED — **does NOT exclude NEW** (matches `reports/c-p2.6-overdue-filter.md` §2: `sla_deadline IS NOT NULL AND < now AND status NOT IN (DONE,CANCELLED)`).
- **Net technician card predicate:** `(assignee == me OR status == NEW) AND (deadline present AND past AND status NOT IN (DONE,CANCELLED))`. A NEW ticket past its deadline satisfies **both** the scope OR-arm (`status=NEW`) and the overdue predicate → it appears, even though it is not assigned to the technician. This is the same shared-queue exposure the technician's NEW status card already has.

## §2 — How the FE card issues the query (file:line)

`frontend/apps/admin/src/pages/TicketsPage.tsx`:
- Overdue card: `SlaCountCard` → `useTicketCount({ overdue: true })` (`:42`) → `GET /api/tickets?overdue=true` reading `PageResponse.total`.
- Status cards: `useTicketCount({ status })` (`:34`); mine card (P2.8): `useTicketCount({ mine: true })` (`:70`).
- The count is the BE's role-scoped `total` — no FE scoping, no FE role-branching. So the card faithfully shows whatever the BE scope+filter returns.

## §3 — Bucket decomposition (real DB numbers, dev `gemek`)

Technician `T = 33e22457-0604-4762-80b2-895f3a19bafb` (1 assigned ticket, overdue):

| Bucket | Definition | Count |
|--------|-----------|------:|
| **card_total** | `(assignee=T OR status=NEW) AND overdue` — what the card shows | **328** |
| (i) assigned-to-me | `overdue AND assignee=T` | 1 |
| (ii) NEW-not-mine | `overdue AND status=NEW AND assignee≠T` (shared queue) | 327 |
| **(iii) leak probe** | card row that is `status≠NEW AND assignee≠T` | **0** |

`(i) + (ii) = 1 + 327 = 328 = card_total`. **(iii) = 0** → every "not mine" row the technician sees is a NEW-and-overdue queue ticket. Second technician (`6b4c076b…`) (iii) = **0** as well.

## §4 — Why (iii) is 0 by construction (not just empirically)

For a row to be in the card it must satisfy `(assignee=me OR NEW) AND overdue`. If such a row is `status≠NEW`, the `OR NEW` arm is false, so the `assignee=me` arm must be true → `assignee=me`. Therefore a card row that is both `status≠NEW` and `assignee≠me` is logically impossible. The `.and(buildScopeSpec)` at `:212` guarantees scope is never bypassed when `overdue` is applied. Confirmed independently: the one non-NEW, overdue, **unassigned** ticket in the DB (an IN_PROGRESS row) is **excluded from all technician scopes** (fails both arms) — it is visible only to ADMIN/BOARD. Scope is doing its job in both directions.

**Global overdue distribution (dev DB, for context):** NEW = 327 (all unassigned), ASSIGNED = 95 (all assigned), IN_PROGRESS = 56 (55 assigned, 1 unassigned). Total overdue = 478. The 327 NEW dominate any technician's overdue card.

## §5 — Root cause: label/semantics, not scope

The card is **scope-correct** but **mislabeled by expectation**. «Trễ hạn» reads as "overdue", and a technician reasonably assumes "my overdue work". In reality the BE-scoped overdue set for a technician = **their own overdue (1) + the entire NEW-overdue shared queue (327)**. Because the NEW queue dwarfs their assignment, the card looks like an all-tickets count. This is identical, by design, to the technician's NEW status card, which also surfaces the full shared NEW queue (the `OR status=NEW` arm is the intended "claimable work" exposure — `reports/c-p2.5-ticketstats-source.md` / `c-p2.6`).

This is consistent and non-leaking, but the **label does not communicate** that the number includes the claimable NEW queue, not just assigned work.

## §6 — Options (NOT implemented — product decision for CTO)

1. **Leave as-is.** Scope-correct and consistent with the NEW status card. The number is "overdue tickets in my view (mine + claimable NEW)". Lowest risk; only a comprehension gap.
2. **Relabel / annotate** the card so it reads as scope-overdue (e.g. clarify it includes the shared NEW queue) — FE-only, no BE change.
3. **"My overdue" semantics** — if the desired meaning is strictly the technician's own overdue work, combine the existing filters: `overdue=true AND mine=true` (P2.8 already supports this composition, ANDs correctly — `reports/c-p2.8-mine-filter.md`). For `T` that yields bucket (i) = **1**. This is a FE query change on the card source (`useTicketCount({ overdue: true, mine: true })`), not a scope/BE fix.

**No security defect exists.** Any change here is a UX/semantics decision, gated to the CTO. Diagnosis ends here per the read-only scope.
