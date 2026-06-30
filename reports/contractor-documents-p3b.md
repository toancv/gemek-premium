# Contractor Documents — FE P3b: lazy-save upload on the create page

Branch `feature/contractor-contract-upload`. Admin app only. NO BE / auth / schema / API-SPEC change.
Model: plain create + immediate upload, NO draft (a created contractor is immediately active).

## Ground truth (verified)
- HEAD `1c4693b` (docs P3a), branch `feature/contractor-contract-upload`. Working tree clean (only untracked `reports/*`).
- P1 `093265b` (BE V23 + endpoints) + `6d8c611` (tests); P2 `5d2df1a` (create/edit pages); P3a `4c76ecf` (documents manager on edit). All present.
- PROGRESS top snapshot authoritative: P3a done + smoked-pending; NEXT = P3b lazy-save on `/new`. Git agrees.

## Reference faithfully reproduced (C3 lazy-save)
`AnnouncementCreatePage.tsx:99-148` `ensureDraftThenUpload`:
- `inFlight` **synchronous ref** = the single-create guarantee (state drives disabled UI; the ref bails a
  same-tick second trigger). `AnnouncementCreatePage.tsx:48-52,111`.
- Order: `if (inFlight.current) return` → `form.validate()` (invalid ⇒ `toast.error` + inline, NO create)
  → set inFlight + lazyBusy → `create.mutateAsync` → on create-fail set inline error + reset guards + return
  (NO orphan/phantom id) → on success upload → `navigate(replace)` to `/:id/edit`; upload-fail still navigates
  with a `state.notice` (the record survived). `AnnouncementCreatePage.tsx:107-148`.
- Edit page reads `location.state.notice` into a banner: `AnnouncementEditPage.tsx:65-69,167`.

### Contractor divergence from the announcement reference
- **NO draft.** "ensure-record-then-upload": `create` makes an immediately-active contractor (not a draft).
- `ContractorDocumentsManager` (P3a) is NOT pre-wired for lazy mode (unlike `AnnouncementAttachmentsManager`,
  which already had `onLazyUpload`/`externalBusy`). It is `{ contractorId }`-only and owns its upload/list
  internally (`ContractorDocumentsManager.tsx:60-117`).

## Decision — parameterize the SAME manager (NOT a duplicate wrapper)
Add optional lazy props to `ContractorDocumentsManager`; this reuses the P3a caps pre-checks
(≤10MB/file, ≤5, ≤50MB total) and the **413 `errorText`** branch verbatim (one source of truth), matching how
C3 extended its attachments manager. Alternative (a separate create-mode component) was rejected: it would
duplicate the pre-check + 413 mapping and risk drift — the task says reuse them verbatim.

### Manager change (additive, edit path byte-for-byte unchanged when new props absent)
- Props → `{ contractorId?: string; onLazyUpload?: (file: File) => Promise<void>; externalBusy?: boolean }`.
- `useContractorDocuments(contractorId ?? '')` — `enabled:!!id` already gates; empty id ⇒ query idle, `data`
  undefined ⇒ `documents=[]`, `isLoading/isError=false` (disabled query is idle, not loading) ⇒ button NOT
  stuck disabled. `hooks.ts:218-223`.
- `busy = upload.isPending || remove.isPending || !!externalBusy`.
- `onPick`: SAME pre-checks first; then `if (onLazyUpload) await onLazyUpload(file)` else
  `await upload.mutateAsync({ id: contractorId!, file })`. (`!` safe — edit always passes id; lazy never
  takes this branch.) Same try/catch → `setError(errorText(err))`.
- Lazy mode list = empty placeholder; the create page redirects on the first successful upload, so the
  list/delete UI never meaningfully renders there (no second source of truth for the list).

## Create-page orchestrator `ensureContractorThenUpload(file)` (ContractorCreatePage)
Race/guard design (the riskiest angle):
1. `if (inFlight.current) return;` — synchronous ref, set BEFORE any await ⇒ a same-tick second trigger bails.
2. `if (!form.validate()) { toast.error(...); return; }` — invalid ⇒ NO create (companyName @NotBlank, P2 rule
   unchanged). `toast.error` because the inline error sits above the manager and may be off-screen (C3 reasoning).
3. set `inFlight=true` + `setLazyBusy(true)`.
4. `create.mutateAsync(toPayload())`. **Create-once guarantee:** single-file input (no `multiple`) + the synchronous
   ref + the button disabling on `externalBusy` ⇒ exactly ONE create even under rapid double-click. NEVER twice.
5. create FAIL ⇒ `form.setFormError(getVnErrorMessage(...))`, reset `inFlight=false` + `lazyBusy=false`, return.
   **No phantom id is stored** — a retry re-enters from step 1 and re-attempts create (and still cannot
   double-create on success, same ref guard).
6. create OK ⇒ `qc.setQueryData(['contractors', id], created)` (seed detail cache so edit renders instantly,
   mirrors `ContractorCreatePage.tsx:38`) ⇒ `uploadDoc.mutateAsync({id, file})` ⇒
   `navigate('/contractors/:id/edit', {replace:true})`. Upload FAIL ⇒ still `navigate(replace)` with
   `state.notice` (record survived; user retries on edit). `navigate(replace)` unmounts `/new` ⇒ no guard reset needed.

### Deterministic "remaining picks" behavior (chosen + documented)
**Create → upload first file → redirect to edit; further picks happen on the edit page.** During the in-flight
create+upload the upload button is disabled (`externalBusy=busy`) and the input is single-file, so NO second
file can be picked on `/new`; after redirect the P3a edit-page manager owns all further uploads. The create
page never holds a document list.

### Edit page (minimal additive)
Add `useLocation` + a yellow notice banner in `ContractorEditForm` (mirror `AnnouncementEditPage.tsx:65-69,167`)
so the create-page upload-failure notice surfaces. No other edit behavior changes.

## Scope discipline
Admin app only. No BE/auth/schema/API-SPEC. No 400 size branch (413-only, per P3a + DECISIONS 2026-06-28).
No draft flag. Field set/validation identical to P2. Resident app / ticket / announcement formatSize bug /
BOARD read-view untouched.

## Verify
No admin vitest harness (do not scaffold). admin `tsc --noEmit` exit 0 + `vite build` 769 modules GREEN
(commit `90ae857` feat; `f14c657` fix). NOTE: `vite build` intermittently fails on Windows with
`[vite:esbuild-transpile] remove …esbuild-<hash>: being used by another process` — a Defender temp-file
lock AFTER "769 modules transformed", not a code error; building with `TEMP`/`TMP` pointed at the scratchpad
sidesteps it and is GREEN.

## `/code-review` high (workflow, 43 agents, 8 finder angles + per-candidate verify)
**0 correctness/data-loss bugs survived verification.** Race/double-create angle CONFIRMED sound: the
synchronous `inFlight` ref (set before any await) + single-file input + `externalBusy` disable ⇒ exactly ONE
create; create-failure leaves NO phantom id; the `created.id`-from-response and redirect were both checked
(the enveloped-response double-create theory was REFUTED). 33 candidates → 26 kept, 7 refuted; all kept are
UX-feedback / DRY in the 3 admin files.

**Fixed (`f14c657`):**
- F3 — lazy create-failure now ALSO toasts (was inline-only; the inline error can be off-screen below the
  documents section). Mirrors the invalid-form branch.
- F5 — manager upload button label now reflects `externalBusy`, so lazy-mode upload shows "Đang tải lên…"
  (the mutation runs on the page, not the manager's own `upload`, so its `isPending` never fired here).

**Accepted as designed / deferred (documented, not drift):**
- Second file picked DURING the in-flight create+upload is dropped (the ref bails it) — this IS the chosen
  deterministic behavior (one file on create → redirect → rest on edit); single-file input + button disable
  cover the normal case, the gap is only a sub-render double-pick race; no saved-data loss. Accept.
- Manager's in-place `setError` path is dead in lazy mode (errors surface via the create-fail toast + the
  edit-page notice) — by design (orchestrator owns failure surfacing). Accept.
- Edit-page notice not cleared on browser Back (can re-show stale) — C3-faithful (AnnouncementEditPage has
  the identical `location.state.notice`→`useState` pattern); deferred with the sibling.
- Save button label keys off `submitting` only (stays "Lưu" while disabled during a lazy upload) — correct:
  a lazy doc upload is not a form save; the button is disabled regardless. Accept.
- Double success toast (create + upload) on one pick — informative (signals the implicit auto-create). Accept.
- DRY: `ensureContractorThenUpload` clones `ensureDraftThenUpload`; 413 mapping + cache-seed duplicated —
  matches the established announcement pattern; a shared `useLazyCreateThenUpload` hook is a cross-module
  refactor, OUT OF SCOPE for this isolate-cleanly phase. Logged as known debt, deferred.
