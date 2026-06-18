# P5 — Stale API-SPEC Entry Resolution

**Date:** 2026-06-18
**Scope:** DOCS ONLY. Resolve the 4 stale entries flagged in `c-p5-apispec-reconciliation.md` §C.
**CTO ruling applied:** each was original intent that MAY have been superseded. Per entry, checked the
controller layer for a functional equivalent (strict duplicate test: must serve the SAME
operation/data, not just a similar name). Equivalent found → REMOVE; none → KEEP `[PLANNED — chưa implement]`.

---

## Verdicts

| # | Stale entry | Code check | Verdict |
|---|---|---|---|
| S1 | `GET /api/contractors/{id}/work-history` (tickets assigned to a contractor) | `GET /api/tickets` binds status, visibility, category, priority, apartmentId, overdue, mine — **no `assignedToContractorId` filter** (TicketController:106-128). Nested `/contracts` returns contracts, not tickets. No endpoint serves tickets-by-contractor. | **KEEP — [PLANNED]** |
| S2 | `GET /api/contracts` (system-wide filtered contract list) | Live `GET /api/contractors/{id}/contracts` (ContractorController:168) lists **one** contractor's contracts, `page`/`size` only — no cross-contractor scope, no `status`/`expiringWithinDays`/`from`/`to` filters. Not covered. | **KEEP — [PLANNED]** |
| S3 | `POST /api/contracts` (top-level create, `contractorId` in body) | `POST /api/contractors/{id}/contracts` (ContractorController:190) creates the same contract with the contractor taken from the path. A contract ALWAYS belongs to a contractor (`createContract(id, request, …)`); no contractor-less contract exists. Same operation → genuine duplicate. | **REMOVE** (replaced by `POST /api/contractors/{id}/contracts`) |
| S4 | `PUT /api/maintenance-schedules/{id}` (update a schedule) | Schedules have create + list only: `POST`/`GET /api/contracts/{id}/schedules` (ContractorController:280-298). No update mapping anywhere. Not covered. | **KEEP — [PLANNED]** |

Summary: 1 removed (S3, proven duplicate), 3 kept as `[PLANNED — chưa implement]` (S1, S2, S4).
No entry removed on a guess — the three keeps each have a concrete code reason no live endpoint
covers their use case.

---

## Edits applied to API-SPEC.md

- **S3 REMOVED:** deleted the `POST /api/contracts` section entirely. Live replacement is the already-
  documented `POST /api/contractors/{id}/contracts`. The contractor-context requirement is inherent
  (a contract needs a contractor), so the top-level form added nothing.
- **S1 / S2 / S4 KEPT:** the prior `⚠️ STALE (awaiting ruling)` banners replaced with a durable
  `🚧 [PLANNED — chưa implement]` marker stating, with file:line, why no live endpoint covers each.
  This is the durable fix for the recurring-flag problem: the next reconciliation reads `[PLANNED]`
  and will NOT re-flag them as stale-vs-code (planned ≠ drift).
- The kept `GET /api/contracts` section is still referenced by `GET /api/contractors/{id}/contracts`
  ("same shape as the GET /api/contracts list item") — that cross-reference remains valid.

Note: this resolution closes §C of `c-p5-apispec-reconciliation.md`. The two pre-existing
self-flagged items there (contract `attachment` endpoints, ticket `slaBreached` param) were already
marked NOT IMPLEMENTED in the spec and were out of scope for this pass.
