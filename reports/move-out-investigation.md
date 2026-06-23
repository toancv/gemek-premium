# Move-out (item d) — pre-UI investigation

Read-only. Confirms the move-out BE contract + existing UI before any UI work. NO code changed.
Reconciled against DECISIONS (move-out conditional-deactivation, primary-contact per-row, occupancy-derived) and
`reports/residency-lifecycle-investigation.md §E`.

## Verdict (up front)
**BE is fully multi-residency-correct, and the admin UI already EXISTS and is correct.** Item (d) is effectively
DONE — NOT net-new. The anticipated "UI phase" is not required unless the CTO wants an additional surface (see
open questions).

---

## 1. Move-out endpoint contract — `{id}` is a RESIDENCY row id (decisive)
- Endpoint: **`POST /api/residents/{id}/move-out`**, ADMIN-only
  (`backend/.../module/resident/ResidentController.java:196-203`; `@PreAuthorize("hasRole('ADMIN')")` :197;
  `@PathVariable UUID id` → `residentService.moveOut(id, req, principal.getId())` :203).
- Service: `ResidentServiceImpl.moveOut(UUID id, ...)` (`:410`) resolves the id via
  **`residentRepository.findById(id)`** (`:413`) → loads **ONE `Resident` (residency) row**. So `{id}` is
  `residents.id` (a single residency / single apartment), **NOT** a user id.
- **Consequence:** move-out ends exactly ONE residency (one apartment). It does NOT iterate or end all of a
  user's residencies. **Multi-residency-correct** — a user active in two apartments who moves out of one keeps
  the other residency. (Already proven green by `P3PlaceResidentIntegrationTest` move-out steps.)

## 2. Conditional deactivation — disable only when no OTHER active residency
- `ResidentServiceImpl.moveOut:441-442`:
  ```java
  if (residentUser != null && !residentRepository.existsActiveByUserId(residentUser.getId())) {
      residentUser.setActive(false); userRepository.save(residentUser);
  }
  ```
- `existsActiveByUserId` (`ResidentRepository.java:169-174`) counts active residency across ANY apartment for the
  user. Because `move_out_date` is set on the leaving row FIRST (`:421`), this answers "does the user still have
  ANY OTHER active residency anywhere?". Account is disabled **only when the answer is false**. §E confirms this
  is correct under multi-residency. **A user moving out of one of two apartments stays ENABLED.** ✅
- Atomic: whole method is `@Transactional` (class-level write tx); if the deactivation write fails the move-out
  rolls back. Re-trigger guarded → `RESIDENT_ALREADY_MOVED_OUT` (`:417-419`).

## 3. Side effects — symmetric counterpart of move-in (all present)
- **move_out_date on the correct single row:** `resident.setMoveOutDate(req.getMoveOutDate())` (`:421`). ✅
- **clears is_primary_contact on THAT row only (per-row):** `if (resident.isPrimaryContact())
  resident.setPrimaryContact(false)` (`:424-425`). §E verified per-residency, NOT user-wide — moving out of apt A
  does not touch the same user's primary flag on apt B. ✅
- **resident_history MOVED_OUT, actor = acting admin:** `appendHistory(saved, "MOVED_OUT",
  req.getMoveOutDate(), principalId, req.getNotes())` (`:431`); `principalId` = `principal.getId()` from the
  controller (the acting admin). ✅
- **occupancy reflects automatically (derived):** occupancy is computed via `OccupancyResolver` from active
  residents (`move_out_date IS NULL`); there is no stored occupancy field to update — ending the residency makes
  the apartment derive AVAILABLE when no other active resident remains. ✅ (per DECISIONS "occupancy derived").

## 4. Auth
- ADMIN only (`@PreAuthorize("hasRole('ADMIN')")`, `ResidentController.java:197`). Not wider. ✅

## 5. Existing admin UI — PRESENT and correct (not net-new)
- `frontend/apps/admin/src/pages/ResidentsPage.tsx`:
  - Hook: `useMoveOutResident` (`frontend/apps/admin/src/api/hooks.ts:130-139`) → `POST
    /residents/{id}/move-out` with `{ moveOutDate, notes? }`; `onSuccess` invalidates `['residents']`;
    `meta.successMessage` top-right toast.
  - Per-row action: **"Kết thúc cư trú"** button (`ResidentsPage.tsx:316-319`) → `openMoveOut(r)`.
  - Confirm dialog (`:529+`): names the resident + apartment (`:536-537`), `VNDatePicker` defaulted to today
    (editable) + optional notes textarea, VN copy stating the real effects (marked moved-out, primary-contact
    removed, **account locked only if no other residency**), and irreversible-from-UI warning.
  - Submit: `moveOutResident.mutateAsync({ id: moveOutTarget.id, moveOutDate, ...notes })` (`:106-107`).
    **`moveOutTarget.id` = `ResidentItem.id` = `residents.id`** (the residency ROW id, `:8-16`) — the correct
    residency-scoped id, matching the BE contract.
  - Moved-out rows render an "Đã chuyển đi · <date>" badge (no re-action), matching the BE re-trigger guard.
- **Multi-residency behavior of the UI:** `/residents` lists one row PER residency, so a user active in two
  apartments appears as two rows, each with its own "Kết thúc cư trú" → per-apartment move-out works naturally
  with the residency-scoped id. ✅
- **Verdict: UI is already complete with a real confirm + date + notes.** No net-new UI required.

## 6. API-SPEC accuracy
- Documented at `docs/API-SPEC.md:689-692` (`POST /api/residents/{id}/move-out`). Text matches the code: sets
  `move_out_date`, clears `is_primary_contact`, appends `resident_history`, conditional `users.active=false`
  only when no remaining active residency, atomic. **No drift.** ✅

---

## Open questions for CTO
1. Item (d) appears DONE (BE correct + UI present). Confirm whether anything ADDITIONAL is wanted, e.g.:
   - a dedicated resident-detail page (today move-out lives on the `/residents` list per-row — DECISIONS chose
     the list as the resident admin surface; no detail route exists), or
   - surfacing move-out from another surface (apartment detail / household view).
   If not, close item (d).
2. `[TODO: verify]` minor UX only: under multi-residency the two same-user rows are visually independent; if the
   CTO wants them grouped/labelled by person, that's a list-presentation enhancement, not a move-out gap.

No fixes, no code edits made.
