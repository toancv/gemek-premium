# Contractor Documents — P2 (FE: dedicated create/edit pages replacing the modal)

Branch `feature/contractor-contract-upload`. Admin app only. No BE / no auth / no schema / no API-SPEC change.
Pure FE consuming the EXISTING contractor create/update/detail endpoints.

## Ground truth (verified)
- HEAD `62e5258`, branch `feature/contractor-contract-upload`. P1 BE commits present: `093265b` (feat table V23 + endpoints), `6d8c611` (test), `62e5258` (docs/API-SPEC). Matches PROGRESS top snapshot (P1 BE done, NEXT = P2 pages). HANDOFF resume pointer was stale — ignored per task.

## What was read (anchors)
- List + retired modal: `frontend/apps/admin/src/pages/ContractorsPage.tsx` (pre-P2 modal at lines 88–124; payload+validation at 24–35).
- Mutation hooks: `frontend/apps/admin/src/api/hooks.ts:189` `useCreateContractor` (POST `/contractors`, `meta.successMessage='Thêm nhà thầu thành công'`, `skipErrorToast`), `:198` `useUpdateContractor` (PUT `/contractors/{id}`), both `onSuccess → invalidateQueries(['contractors'])`.
- Structural template: `AnnouncementCreatePage.tsx` / `AnnouncementEditPage.tsx` + shared `AnnouncementComposer.tsx` (`useAnnouncementForm` hook + `AnnouncementComposeFields` presentational split; loader/guard wrapper; `navigate` save flow; inline `getVnErrorMessage` + MutationCache toast).
- Routing: `App.tsx:74–77` (announcement `/new` + `/:id/edit` precedent).

## Contractor field list (reproduced EXACTLY from the retired modal — unchanged set)
| Field | Control | Required | Payload mapping |
|-------|---------|----------|-----------------|
| companyName | text | **yes** (`Tên công ty là bắt buộc.`) | `companyName.trim()` |
| contactPerson | text | no | as-is |
| phone | text | no | as-is |
| email | email | no | as-is |
| specialty | select (9 enum opts, default `OTHER`) | no | as-is |
| address (`Địa chỉ`) | text | no | as-is |
| taxCode (`Mã số thuế`) | text | no | `taxCode.trim() || null` |
| notes (`Ghi chú`) | textarea | no | `notes.trim() || null` |

No field added/removed. VN labels via `t('contractors.*')` where keys exist (companyName/contactPerson/phone/email/specialty); address/taxCode/notes hardcoded VN exactly as the modal.

## Endpoints reused (UNCHANGED — no API-SPEC change)
- POST `/api/contractors` (ADMIN) — create. `ContractorController.java:104`.
- GET `/api/contractors/{id}` (ADMIN/TECH/BOARD) — edit-page prefill via new `useContractor(id)` hook. `ContractorController.java:120`.
- PUT `/api/contractors/{id}` (ADMIN) — update. `ContractorController.java:135`.
All pre-existing; no new endpoint, no contract change.

## Files added / changed
- **NEW** `components/ContractorForm.tsx` — `useContractorForm(initial?)` hook (state + `validate` + `toPayload`) + `ContractorFormFields` presentational. `CONTRACTOR_SPECIALTIES` moved here from the list page.
- **NEW** `pages/ContractorCreatePage.tsx` — route `/contractors/new`. Create success → `navigate('/contractors/:id/edit')` of the new record (CTO-approved, sets up P3).
- **NEW** `pages/ContractorEditPage.tsx` — route `/contractors/:id/edit`. Loader/guard (loading spinner / not-found) → `ContractorEditForm` keyed on id. Save → STAYS on page (P3 documents section lands here); success via MutationCache toast.
- `api/hooks.ts` — added `useContractor(id)` (GET `/contractors/{id}`, key `['contractors', id]`, `enabled: !!id`).
- `pages/ContractorsPage.tsx` — removed modal + create/update mutation wiring; "Add" → `navigate('/contractors/new')`; row "Edit" → `navigate('/contractors/:id/edit')`. Modal was inline-only (no shared component) — grep confirms the mutations had no other consumer, so removal is clean.
- `App.tsx` — imports + 2 routes, gated `RequireRole roles={['ADMIN']}` (BE writes are ADMIN-only; list stays ADMIN+BOARD).

## Save / feedback flow
- Create OK → redirect to new record's edit page. Update OK → stay on edit page.
- Errors inline via `getVnErrorMessage(err?.response?.data?.error)`; success via `meta.successMessage` (admin toast top-right).
- List + detail refetch: existing `onSuccess invalidateQueries(['contractors'])` prefix-matches both `['contractors', params]` (list) and `['contractors', id]` (detail).

## NOT done (P3 owns)
No documents/upload UI, no attachments section, no TEMP_HIDDEN placeholder. Edit page left extensible (a single comment marks where P3 appends the documents manager).

## Verification
- `npm run build` (admin) GREEN: `tsc && vite build`, 768 modules, built OK (one transient Windows esbuild temp-file lock on first run, passed clean on retry — not a code error).
- No admin vitest harness exists (known backlog gap) — not scaffolded this phase per task; verified by build-green + pending CTO :80 manual smoke.

## `/code-review` (high, workflow — 33 agents, finder + independent verify) triage
8 findings survived verification. **Fixed 4** (each aligns the new pages with the C2.3b sibling or restores modal behaviour):
1. **CONFIRMED correctness** — create `submit()` had no synchronous in-flight guard → a double-click could POST twice and persist a duplicate contractor. Added a `useRef` `inFlight` guard, exactly like `AnnouncementCreatePage`.
2. **CONFIRMED regression** — Enter-to-submit was lost (modal was a `<form onSubmit>`; new pages were bare divs + `type=button`). Wrapped both pages' fields+buttons in `<form onSubmit>`, Save = `type=submit`.
3. **CONFIRMED UX gap** — create did not seed the detail cache, forcing a refetch + risking a transient not-found on the new edit page. Added `qc.setQueryData(['contractors', created.id], created)` before navigate, mirroring the sibling.
4. **CONFIRMED cleanup** — edit-loader hand-rolled a spinner SVG. Replaced with shared `PageSpinner` from `@gemek/ui`.

**Skipped (out of scope / pre-existing / matches sibling — logged, not drift):**
- `ContractorsPage.tsx:54` `c.isActive` vs serialized `active` → always shows INACTIVE. **Real but PRE-EXISTING** on an unchanged line (verifier itself flagged out-of-scope); not introduced by this diff → left for a dedicated fix.
- `created.id` undefined *if* the BE ever enveloped the response → currently safe (POST returns un-enveloped `ContractorResponse`); hypothetical-future, mitigated by the cache seed.
- Edit form not re-seeded from the PUT response → matches the sibling announcement edit; only affects BE-side normalization divergence.
- `useUpdateContractor` broad `['contractors']` invalidation → pre-existing hook (untouched by P2); the announcement sibling uses the same broad key. Perf micro-opt, deferred.
- `any` typing of the contractor entity → matches the sibling's `any` convention; typed-DTO extraction deferred (consistent with the P1 triage).

Re-verified after fixes: admin `tsc --noEmit` exit 0 + `vite build` green (599.81 kB bundle; transient esbuild Windows temp-unlink race needed a retry — not a code error).
